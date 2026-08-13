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
import java.io.OutputStream;
import com.huawei.hms.ads.HwAds;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HwAds.init(this);
        webView = new WebView(this);
        setContentView(webView);
        
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setClickable(true);
        
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void downloadFile(String b64, String name, String msg) {
                try {
                    if (b64 == null || b64.isEmpty()) return;
                    if (b64.startsWith("data:")) b64 = b64.substring(b64.indexOf(",") + 1);
                    
                    byte[] bt = Base64.decode(b64, Base64.DEFAULT);
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    
                    String mime = "application/octet-stream";
                    if (name.endsWith(".pdf")) mime = "application/pdf";
                    else if (name.endsWith(".txt")) mime = "text/plain";
                    
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u != null) {
                        OutputStream o = getContentResolver().openOutputStream(u);
                        o.write(bt); o.close();
                        if (msg != null) runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {}
            }
            
            @JavascriptInterface
            public void startIAP(String type) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connecting to Huawei IAP...", Toast.LENGTH_LONG).show());
            }

            @JavascriptInterface
            public void showToast(String msg) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
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
            public boolean onJsAlert(WebView view, String url, String message, final android.webkit.JsResult result) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
                result.confirm();
                return true;
            }
            
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final android.webkit.JsResult result) {
                if (message.contains("Watch ad")) {
                    result.confirm();
                    return true;
                }
                return super.onJsConfirm(view, url, message, result);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                String js = "javascript:(function() { " +
                "  function fixApp() { " +
                "    var s = document.getElementById('splash-screen'); " +
                "    if(s) { s.style.display='none'; s.style.pointerEvents='none'; } " +
                "    " +
                "    /* Intercept All Clicks on Download Links */ " +
                "    if (!window.isBridgeSetup) { " +
                "      var oldClick = HTMLAnchorElement.prototype.click; " +
                "      HTMLAnchorElement.prototype.click = function() { " +
                "        if (this.href && (this.href.startsWith('blob:') || this.href.startsWith('data:') || this.download)) { " +
                "          var name = this.download || 'file'; " +
                "          fetch(this.href).then(r => r.blob()).then(blob => { " +
                "            var reader = new FileReader(); " +
                "            reader.onloadend = function() { " +
                "              var l = document.querySelector('.lang-sel')?.value || 'en'; " +
                "              var m = l.includes('ar') ? '✅ تم حفظ الملف بنجاح' : '✅ File saved successfully'; " +
                "              AndroidBridge.downloadFile(reader.result, name, m); " +
                "            }; " +
                "            reader.readAsDataURL(blob); " +
                "          }); " +
                "          return; " +
                "        } " +
                "        oldClick.call(this); " +
                "      }; " +
                "      window.isBridgeSetup = true; " +
                "    } " +
                "    " +
                "    window.simulateUpgrade = function() { AndroidBridge.startIAP('PRO'); }; " +
                "    window.buyCredits = function() { AndroidBridge.startIAP('CREDITS'); }; " +
                "    " +
                "    /* Translate Credit Modal */ " +
                "    var lang = document.querySelector('.lang-sel')?.value || 'en'; " +
                "    var isAr = lang.includes('ar'); " +
                "    document.querySelectorAll('.modal-box *').forEach(function(el) { " +
                "      if(el.children.length > 0) return; " +
                "      var t = el.innerHTML; " +
                "      if(t.includes('شراء نقاط')) el.innerHTML = isAr ? '💎 شراء نقاط' : '💎 Buy Credits'; " +
                "      if(t.includes('نقطة')) el.innerHTML = t.replace('نقطة', isAr ? 'نقطة' : 'Credits'); " +
                "      if(t.includes('الدفع آمن')) el.innerHTML = isAr ? 'الدفع آمن عبر Huawei' : 'Secure payment via Huawei'; " +
                "    }); " +
                "  } " +
                "  fixApp(); setInterval(fixApp, 2000); " +
                "})(); void(0);";
                view.loadUrl(js);
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
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
            filePathCallback.onReceiveValue(res); filePathCallback = null;
        }
    }
}
