package com.pixelpdf.app;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.media.MediaScannerConnection;
import java.io.OutputStream;
import java.io.File;
import com.huawei.hms.ads.HwAds;
import com.huawei.hms.ads.reward.Reward;
import com.huawei.hms.ads.reward.RewardAd;
import com.huawei.hms.ads.reward.RewardAdLoadListener;
import com.huawei.hms.ads.reward.RewardAdStatusListener;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private RewardAd rewardAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HwAds.init(this);
        webView = new WebView(this);
        setContentView(webView);
        
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void downloadFile(String b64, String name, String msg) {
                try {
                    if (b64.startsWith("data:")) b64 = b64.substring(b64.indexOf(",") + 1);
                    byte[] bt = Base64.decode(b64, Base64.DEFAULT);
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    String mime = name.endsWith(".pdf") ? "application/pdf" : (name.endsWith(".txt") ? "text/plain" : "image/jpeg");
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u != null) {
                        OutputStream o = getContentResolver().openOutputStream(u);
                        o.write(bt); o.close();
                        MediaScannerConnection.scanFile(MainActivity.this, new String[]{Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + "/" + name}, new String[]{mime}, null);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {}
            }
            
            @JavascriptInterface
            public void startIAP(String type) {
                String productId = "";
                if (type.equals("PRO")) productId = "pro_version";
                else if (type.equals("500")) productId = "credits_500";
                else if (type.equals("1000")) productId = "credits_1000";
                else if (type.equals("3000")) productId = "credits_3000";
                
                final String finalId = productId;
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connecting to Huawei IAP for: " + finalId, Toast.LENGTH_LONG).show());
            }

            @JavascriptInterface
            public void showVideoAd() {
                runOnUiThread(() -> loadRewardAd());
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
            @Override
            public boolean onJsAlert(WebView v, String u, String m, android.webkit.JsResult r) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show());
                r.confirm(); return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.loadUrl("javascript:(function() { " +
                "  window.simulateUpgrade = function() { AndroidBridge.startIAP('PRO'); }; " +
                "  window.buyCredits = function(amt) { AndroidBridge.startIAP(amt); }; " +
                "  window.watchAd = function() { AndroidBridge.showVideoAd(); }; " +
                "  /* Fix Modal Translation */ " +
                "  var lang = document.querySelector('.lang-sel')?.value || 'en'; " +
                "  var isAr = lang.includes('ar'); " +
                "  document.querySelectorAll('.modal-box *').forEach(function(el) { " +
                "    if(el.children.length > 0) return; " +
                "    var t = el.innerHTML; " +
                "    if(t.includes('شراء نقاط')) el.innerHTML = isAr ? '💎 شراء نقاط' : '💎 Buy Credits'; " +
                "    if(t.includes('نقطة')) el.innerHTML = t.replace('نقطة', isAr ? 'نقطة' : 'Credits'); " +
                "    if(t.includes('الدفع آمن')) el.innerHTML = isAr ? 'الدفع آمن عبر Huawei' : 'Secure payment via Huawei'; " +
                "  }); " +
                "})();");
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void loadRewardAd() {
        rewardAd = new RewardAd(this, "testy7m52sqo74"); // Test ID for now
        rewardAd.loadAd(new com.huawei.hms.ads.AdParam.Builder().build(), new RewardAdLoadListener() {
            @Override
            public void onRewardAdFailedToLoad(int errorCode) {
                Toast.makeText(MainActivity.this, "Ad failed to load. Try again later.", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onRewardedLoaded() { rewardAd.show(MainActivity.this, new RewardAdStatusListener() {
                @Override
                public void onRewarded(Reward reward) {
                    webView.loadUrl("javascript:addCredits(25);");
                    Toast.makeText(MainActivity.this, "Success! +25 Credits added.", Toast.LENGTH_SHORT).show();
                }
            }); }
        });
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        if (r == 1 && filePathCallback != null) {
            Uri[] res = (c == RESULT_OK && d != null) ? (d.getClipData() != null ? new Uri[d.getClipData().getItemCount()] : new Uri[]{d.getData()}) : null;
            if (res != null && d.getClipData() != null) for (int i=0; i<d.getClipData().getItemCount(); i++) res[i] = d.getClipData().getItemAt(i).getUri();
            filePathCallback.onReceiveValue(res); filePathCallback = null;
        }
    }
}
