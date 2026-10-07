package app.ryadom.messenger;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final String HOME="https://ryadom-messenger.pugnu.chatgpt.site/";
    private static final String HOST="ryadom-messenger.pugnu.chatgpt.site";
    private static final int PICK=11, PERMISSIONS=12, SAVE=13;
    private WebView web;
    private LinearLayout errorPanel;
    private ProgressBar progress;
    private ValueCallback<Uri[]> chooser;
    private PermissionRequest mediaRequest;
    private String downloadUrl, downloadCookie;
    private byte[] downloadBytes;
    private boolean downloading=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean trusted(String value) {
        if(value==null)return false;
        Uri u=Uri.parse(value);
        return "https".equals(u.getScheme()) && HOST.equals(u.getHost()) && (u.getPort()==-1||u.getPort()==443);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(17,19,21));setContentView(root);
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return WindowInsets.CONSUMED;});
        }
        web=new WebView(this);web.setBackgroundColor(Color.rgb(17,19,21));root.addView(web,new FrameLayout.LayoutParams(-1,-1));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);root.addView(progress,new FrameLayout.LayoutParams(-1,6));
        errorPanel=new LinearLayout(this);errorPanel.setOrientation(LinearLayout.VERTICAL);errorPanel.setGravity(Gravity.CENTER);errorPanel.setPadding(32,32,32,32);errorPanel.setBackgroundColor(Color.rgb(17,19,21));
        TextView title=new TextView(this);title.setText("Не удалось подключиться\nПроверь интернет и попробуй ещё раз");title.setTextColor(Color.WHITE);title.setTextSize(18);title.setGravity(Gravity.CENTER);errorPanel.addView(title);
        Button retry=new Button(this);retry.setText("Повторить");retry.setOnClickListener(v->{errorPanel.setVisibility(View.GONE);web.loadUrl(HOME);});errorPanel.addView(retry);root.addView(errorPanel,new FrameLayout.LayoutParams(-1,-1));errorPanel.setVisibility(View.GONE);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setMediaPlaybackRequiresUserGesture(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){String url=request.getUrl().toString();if(trusted(url))return false;if("https".equals(request.getUrl().getScheme())){try{startActivity(new Intent(Intent.ACTION_VIEW,request.getUrl()));}catch(Exception e){message("Не удалось открыть ссылку");}}return true;}
            @Override public void onPageFinished(WebView view,String url){CookieManager.getInstance().flush();}
            @Override public void onReceivedError(WebView view,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())errorPanel.setVisibility(View.VISIBLE);}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame()&&e.getStatusCode()>=400)errorPanel.setVisibility(View.VISIBLE);}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int p){progress.setProgress(p);progress.setVisibility(p==100?View.GONE:View.VISIBLE);}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(!trusted(view.getUrl()))return false;
                if(chooser!=null)chooser.onReceiveValue(null);chooser=callback;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");
                String[] types=Arrays.stream(params.getAcceptTypes()).filter(s->s!=null&&s.contains("/")).toArray(String[]::new);
                if(types.length==1)intent.setType(types[0]);else if(types.length>1)intent.putExtra(Intent.EXTRA_MIME_TYPES,types);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
                try{startActivityForResult(intent,PICK);}catch(Exception e){chooser.onReceiveValue(null);chooser=null;message("Не удалось открыть файлы");}return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request){runOnUiThread(()->{
                if(!trusted(request.getOrigin().toString())||!trusted(web.getUrl())){request.deny();return;}
                if(mediaRequest!=null)mediaRequest.deny();mediaRequest=request;
                List<String> needed=new ArrayList<>();
                for(String resource:request.getResources()){
                    if(resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.CAMERA);
                    if(resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.RECORD_AUDIO);
                }
                if(needed.isEmpty())grantMedia();else requestPermissions(needed.toArray(new String[0]),PERMISSIONS);
            });}
            @Override public void onPermissionRequestCanceled(PermissionRequest request){if(mediaRequest==request)mediaRequest=null;}
        });
        web.setDownloadListener((url,ua,disposition,mime,size)->{
            if(downloading){message("Сначала сохрани предыдущий файл");return;}
            if(!trusted(web.getUrl())||(!trusted(url)&&!url.startsWith("blob:"+HOME.substring(0,HOME.length()-1)+"/"))){message("Загрузка недоступна");return;}
            downloading=true;downloadBytes=null;downloadUrl=url;downloadCookie=CookieManager.getInstance().getCookie(url);
            String name=URLUtil.guessFileName(url,disposition,mime);
            if(url.startsWith("blob:")){
                name="ryadom-recovery.txt";
                String script="window.__ryadomFile=null;fetch("+JSONObject.quote(url)+").then(r=>r.blob()).then(b=>{if(b.size>20971520)throw Error();let r=new FileReader();r.onload=()=>{window.__ryadomFile=r.result};r.onerror=()=>{window.__ryadomFile='error'};r.readAsDataURL(b)}).catch(()=>window.__ryadomFile='error');";
                web.evaluateJavascript(script,null);pollBlob(0,name,mime);
            }else chooseSave(name,mime);
        });
        web.loadUrl(HOME);
    }
    private void grantMedia(){
        PermissionRequest r=mediaRequest;mediaRequest=null;if(r==null)return;
        if(!trusted(web.getUrl())){r.deny();return;}
        List<String> allowed=new ArrayList<>();for(String resource:r.getResources()){
            if(resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)allowed.add(resource);
            if(resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)allowed.add(resource);
        }if(allowed.isEmpty())r.deny();else r.grant(allowed.toArray(new String[0]));
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==PERMISSIONS)grantMedia();}
    private void pollBlob(int count,String name,String mime){
        if(isFinishing()||!trusted(web.getUrl())||count>75){resetDownload();message("Не удалось подготовить файл");return;}
        web.evaluateJavascript("window.__ryadomFile",result->{try{
            Object parsed=new JSONTokener(result).nextValue();
            if(parsed==JSONObject.NULL){handler.postDelayed(()->pollBlob(count+1,name,mime),200);return;}
            String data=String.valueOf(parsed);if(!data.startsWith("data:")||data.length()>30000000)throw new IOException();
            downloadBytes=Base64.decode(data.substring(data.indexOf(',')+1),Base64.DEFAULT);web.evaluateJavascript("delete window.__ryadomFile",null);chooseSave(name,mime);
        }catch(Exception e){resetDownload();message("Не удалось подготовить файл");}});
    }
    private void chooseSave(String name,String mime){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime==null?"application/octet-stream":mime.split(";")[0]);i.putExtra(Intent.EXTRA_TITLE,name);try{startActivityForResult(i,SAVE);}catch(Exception e){resetDownload();message("Не удалось открыть сохранение");}}
    private void resetDownload(){downloadUrl=null;downloadCookie=null;downloadBytes=null;downloading=false;}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);
        if(code==PICK&&chooser!=null){Uri[] uris=null;if(result==RESULT_OK&&data!=null){if(data.getClipData()!=null){uris=new Uri[data.getClipData().getItemCount()];for(int i=0;i<uris.length;i++)uris[i]=data.getClipData().getItemAt(i).getUri();}else if(data.getData()!=null)uris=new Uri[]{data.getData()};}chooser.onReceiveValue(uris);chooser=null;}
        if(code==SAVE){if(result!=RESULT_OK||data==null||data.getData()==null){resetDownload();return;}Uri target=data.getData();String url=downloadUrl,cookie=downloadCookie;byte[] bytes=downloadBytes;
            new Thread(()->{HttpURLConnection connection=null;try(OutputStream out=getContentResolver().openOutputStream(target)){
                if(out==null)throw new IOException();
                if(bytes!=null)out.write(bytes);else{
                    if(!trusted(url))throw new IOException();connection=(HttpURLConnection)new URL(url).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(30000);if(cookie!=null)connection.setRequestProperty("Cookie",cookie);if(connection.getResponseCode()!=200)throw new IOException();
                    try(InputStream in=connection.getInputStream()){byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>21*1024*1024)throw new IOException();out.write(buffer,0,n);}}
                }runOnUiThread(()->message("Файл сохранён"));
            }catch(Exception e){runOnUiThread(()->message("Не удалось сохранить файл. Попробуй ещё раз."));}finally{if(connection!=null)connection.disconnect();runOnUiThread(this::resetDownload);}}).start();
        }
    }
    private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if(errorPanel.getVisibility()==View.VISIBLE){super.onBackPressed();return;}web.evaluateJavascript("(()=>{let m=document.querySelector('#modal');if(m?.open){m.close();return true}let a=document.querySelector('#app');if(a?.classList.contains('inchat')){a.classList.remove('inchat');return true}return false})()",r->{if(!"true".equals(r)){if(web.canGoBack())web.goBack();else super.onBackPressed();}});}
    @Override protected void onPause(){super.onPause();if(web!=null){web.evaluateJavascript("if(typeof recorder!=='undefined'&&recorder?.state==='recording')recorder.stop()",null);web.onPause();CookieManager.getInstance().flush();}}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(chooser!=null)chooser.onReceiveValue(null);if(mediaRequest!=null)mediaRequest.deny();if(web!=null){web.loadUrl("about:blank");web.destroy();}super.onDestroy();}
}
