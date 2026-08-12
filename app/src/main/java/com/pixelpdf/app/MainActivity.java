
package com.pixelpdf.app;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private final static int FILE_CHOOSER_RESULT_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // JavaScript Bridge for Downloads & Language Sync
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void downloadFile(String b64, String name, String msg) {
                try {
                    if (b64.startsWith("data:")) b64 = b64.substring(b64.indexOf(",") + 1);
                    byte[] bt = Base64.decode(b64, Base64.DEFAULT);
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, name.endsWith(".pdf") ? "application/pdf" : "image/jpeg");
                    if (Build.VERSION.SDK_INT >= 29) {
                        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    }
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u != null) {
                        OutputStream o = getContentResolver().openOutputStream(u);
                        o.write(bt);
                        o.close();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {}
            }
        }, "AndroidDownload");

        // Handle File Uploads (Multiple Support)
        webView.setWebChromeClient(new WebChromeClient() {
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> f, FileChooserParams p) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = f;
                Intent i = p.createIntent();
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(i, "Select Files"), FILE_CHOOSER_RESULT_CODE);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        // Intercept Blob Downloads and Sync Language
        webView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView v, String u) {
                v.loadUrl("javascript:(function() { " +
                "  function getMsg() { " +
                "    var l = document.querySelector('.lang-sel')?.value || localStorage.getItem('pixelpdf_lang') || document.documentElement.lang || 'en'; " +
                "    if(l.toLowerCase().includes('ar')) return '✅ تم حفظ الملف بنجاح'; " +
                "    if(l.toLowerCase().includes('fr')) return '✅ Enregistré avec succès'; " +
                "    return '✅ File saved successfully'; " +
                "  } " +
                "  window.saveAs = function(b, n) { " +
                "    var r = new FileReader(); " +
                "    r.onloadend = function() { AndroidDownload.downloadFile(r.result, n, getMsg()); }; " +
                "    r.readAsDataURL(b); " +
                "  }; " +
                "  var old = HTMLAnchorElement.prototype.click; " +
                "  HTMLAnchorElement.prototype.click = function() { " +
                "    if (this.href.startsWith('blob:') || this.download) { " +
                "      var n = this.download || 'file'; " +
                "      fetch(this.href).then(r => r.blob()).then(b => { " +
                "        var rd = new FileReader(); " +
                "        rd.onloadend = function() { AndroidDownload.downloadFile(rd.result, n, getMsg()); }; " +
                "        rd.readAsDataURL(b); " +
                "      }); " +
                "    } else old.call(this); " +
                "  }; " +
                "})()");
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        if (r == FILE_CHOOSER_RESULT_CODE) {
            if (filePathCallback == null) return;
            Uri[] res = null;
            if (c == RESULT_OK && d != null) {
                if (d.getClipData() != null) {
                    int cnt = d.getClipData().getItemCount();
                    res = new Uri[cnt];
                    for (int i = 0; i < cnt; i++) res[i] = d.getClipData().getItemAt(i).getUri();
                } else if (d.getData() != null) {
                    res = new Uri[]{d.getData()};
                }
            }
            filePathCallback.onReceiveValue(res);
            filePathCallback = null;
        } else {
            super.onActivityResult(r, c, d);
        }
    }
}
