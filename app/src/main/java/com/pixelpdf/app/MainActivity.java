package com.pixelpdf.app;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.huawei.hms.ads.AdParam;
import com.huawei.hms.ads.BannerAdSize;
import com.huawei.hms.ads.HwAds;
import com.huawei.hms.ads.InterstitialAd;
import com.huawei.hms.ads.SplashAd;
import com.huawei.hms.ads.reward.Reward;
import com.huawei.hms.ads.reward.RewardAd;
import com.huawei.hms.ads.reward.RewardAdStatusListener;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    
    // Ad Unit IDs
    private static final String AD_REWARDED = "f95ziipjhl";
    private static final String AD_SPLASH = "n16zcrjokr";
    private static final String AD_BANNER = "c3mwj5uc1a";
    private static final String AD_INTERSTITIAL = "w07e1f28c6";

    private RewardAd rewardAd;
    private InterstitialAd interstitialAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize Huawei Ads
        HwAds.init(this);
        
        webView = new WebView(this);
        setContentView(webView);
        
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // JavaScript Interface for Downloads and Ads/IAP
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void downloadFile(String b64, String name, String msg) {
                saveFileToDownloads(b64, name, msg);
            }

            @JavascriptInterface
            public void showRewardedAd() {
                runOnUiThread(() -> loadAndShowRewardedAd());
            }

            @JavascriptInterface
            public void showInterstitialAd() {
                runOnUiThread(() -> loadAndShowInterstitialAd());
            }
            
            @JavascriptInterface
            public void triggerPurchase(String productId) {
                // Mocking IAP for this build, usually requires complex setup
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connecting to Huawei IAP for: " + productId, Toast.LENGTH_SHORT).show());
            }
        }, "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> f, FileChooserParams p) {
                filePathCallback = f;
                Intent i = p.createIntent();
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(i, "Select Files"), 1);
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String u) {
                injectJavaScriptBridge(v);
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
        
        // Load Splash Ad if needed (usually in a separate activity, but we can simulate or show Interstitial)
        loadAndShowInterstitialAd();
    }

    private void saveFileToDownloads(String b64, String name, String msg) {
        try {
            if (b64.startsWith("data:")) b64 = b64.substring(b64.indexOf(",") + 1);
            byte[] bt = Base64.decode(b64, Base64.DEFAULT);
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            String mime = "application/octet-stream";
            if (name.endsWith(".pdf")) mime = "application/pdf";
            else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) mime = "image/jpeg";
            else if (name.endsWith(".png")) mime = "image/png";
            else if (name.endsWith(".txt")) mime = "text/plain";
            
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u != null) {
                OutputStream o = getContentResolver().openOutputStream(u);
                o.write(bt);
                o.close();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Download Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void injectJavaScriptBridge(WebView v) {
        v.loadUrl("javascript:(function() { " +
            "  function getMsg(type) { " +
            "    var l = document.documentElement.lang || 'en'; " +
            "    if(l=='ar') return type=='save' ? '✅ تم الحفظ بنجاح' : 'جاري التحميل...'; " +
            "    if(l=='fr') return type=='save' ? '✅ Enregistré avec succès' : 'Téléchargement...'; " +
            "    return type=='save' ? '✅ Saved successfully' : 'Downloading...'; " +
            "  } " +
            "  window.saveAs = function(b, n) { " +
            "    var r = new FileReader(); " +
            "    r.onloadend = function() { AndroidBridge.downloadFile(r.result, n, getMsg('save')); }; " +
            "    r.readAsDataURL(b); " +
            "  }; " +
            "  window.HuaweiAds = { " +
            "    showRewarded: function() { AndroidBridge.showRewardedAd(); }, " +
            "    showInterstitial: function() { AndroidBridge.showInterstitialAd(); } " +
            "  }; " +
            "  window.HuaweiIAP = { " +
            "    buy: function(id) { AndroidBridge.triggerPurchase(id); } " +
            "  }; " +
            "  var old = HTMLAnchorElement.prototype.click; " +
            "  HTMLAnchorElement.prototype.click = function() { " +
            "    if (this.href.startsWith('blob:') || this.download) { " +
            "      var n = this.download || 'file'; " +
            "      fetch(this.href).then(r => r.blob()).then(b => { " +
            "        var rd = new FileReader(); rd.onloadend = function() { AndroidBridge.downloadFile(rd.result, n, getMsg('save')); }; rd.readAsDataURL(b); " +
            "      }); " +
            "    } else old.call(this); " +
            "  }; " +
            "})()");
    }

    private void loadAndShowRewardedAd() {
        rewardAd = new RewardAd(this, AD_REWARDED);
        rewardAd.loadAd(new AdParam.Builder().build(), new RewardAdStatusListener() {
            @Override
            public void onRewardAdLoaded() {
                rewardAd.show(MainActivity.this, new RewardAdStatusListener() {
                    @Override
                    public void onRewarded(Reward reward) {
                        webView.loadUrl("javascript:if(window.onAdRewarded) window.onAdRewarded(" + reward.getAmount() + ");");
                        Toast.makeText(MainActivity.this, "Reward earned!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void loadAndShowInterstitialAd() {
        interstitialAd = new InterstitialAd(this);
        interstitialAd.setAdId(AD_INTERSTITIAL);
        interstitialAd.loadAd(new AdParam.Builder().build());
        interstitialAd.setAdListener(new com.huawei.hms.ads.AdListener() {
            @Override
            public void onAdLoaded() {
                if (interstitialAd.isLoaded()) interstitialAd.show(MainActivity.this);
            }
        });
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        if (r == 1 && filePathCallback != null) {
            Uri[] res = null;
            if (c == RESULT_OK && d != null) {
                if (d.getClipData() != null) {
                    res = new Uri[d.getClipData().getItemCount()];
                    for (int i=0; i<d.getClipData().getItemCount(); i++) res[i] = d.getClipData().getItemAt(i).getUri();
                } else if (d.getData() != null) res = new Uri[]{d.getData()};
            }
            filePathCallback.onReceiveValue(res);
            filePathCallback = null;
        } else {
            super.onActivityResult(r, c, d);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
