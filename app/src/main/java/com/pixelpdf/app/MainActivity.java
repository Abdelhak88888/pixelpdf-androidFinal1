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
import com.huawei.hms.iap.Iap;
import com.huawei.hms.iap.IapClient;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // تفعيل إشهارات هواوي
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
                    v.put(MediaStore.MediaColumns.MIME_TYPE, name.endsWith(".pdf") ? "application/pdf" : "image/jpeg");
                    if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u != null) {
                        OutputStream o = getContentResolver().openOutputStream(u);
                        o.write(bt); o.close();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {}
            }
            
            @JavascriptInterface
            public void buyCredits() {
                // كود استدعاء نظام الشراء من هواوي
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connecting to Huawei IAP...", Toast.LENGTH_SHORT).show());
                // هنا سيتم استدعاء نافذة الدفع الخاصة بهواوي
            }
        }, "AndroidDownload");

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
            public void onPageFinished(WebView view, String url) {
                String js = "javascript:(function() { " +
                "  function fixApp() { " +
                "    var s = document.getElementById('splash-screen'); " +
                "    if(s) { s.style.display='none'; s.style.pointerEvents='none'; } " +
                "    window.smartDownload = function(dataUrl, filename) { " +
                "      var l = document.querySelector('.lang-sel')?.value || 'en'; " +
                "      var m = l.includes('ar') ? '✅ تم حفظ الملف بنجاح' : (l.includes('fr') ? '✅ Enregistré avec succès' : '✅ File saved successfully'); " +
                "      AndroidDownload.downloadFile(dataUrl, filename, m); " +
                "    }; " +
                "    window.buyCredits = function() { AndroidDownload.buyCredits(); }; " +
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
