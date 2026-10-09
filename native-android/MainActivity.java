package com.error.messenger;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.*;
import android.database.sqlite.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import android.provider.*;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import com.google.android.gms.common.api.Status;
import org.json.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;

public class MainActivity extends Activity {
  final int BLACK=0xff080809,WHITE=0xfff5f5f5,GRAY=0xff95959a,CARD=0xff1c1c1e,EDGE=0xff303034;
  final String SERVICE="com.error.messenger.offline.v1";
  SQLiteDatabase db; SharedPreferences prefs; ConnectionsClient nearby; LinearLayout root,body; ScrollView scroller;
  String myId,myName,tab="chats",openChat=null; boolean scanning=false, recording=false;
  HashMap<String,String> epToId=new HashMap<>(),epToName=new HashMap<>(),idToEp=new HashMap<>();
  MediaRecorder recorder; File recorded; EditText draft;
  final Strategy STRATEGY=Strategy.P2P_CLUSTER;
  int dp(float x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
  GradientDrawable back(int color,int radius,boolean line){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(line)d.setStroke(dp(1),EDGE);return d;}
  TextView text(String s,int size,int color,boolean strong){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(strong)t.setTypeface(null,1);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
  LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
  LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(0);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
  void pad(View v,int a,int b,int c,int d){v.setPadding(dp(a),dp(b),dp(c),dp(d));}
  void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
  void stretch(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,0,1));}
  void gap(LinearLayout p,int h){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
  TextView button(String label,boolean primary,Runnable cb){TextView t=text(label,14,primary?BLACK:WHITE,true);t.setGravity(Gravity.CENTER);pad(t,12,12,12,12);t.setBackground(back(primary?WHITE:CARD,13,!primary));t.setOnClickListener(v->cb.run());return t;}
  ImageView logo(){ImageView image=new ImageView(this);image.setImageResource(R.drawable.error_logo);image.setScaleType(ImageView.ScaleType.FIT_CENTER);return image;}
  EditText field(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(WHITE);e.setHintTextColor(GRAY);e.setTextSize(15);e.setHint(hint);pad(e,12,11,12,11);e.setBackground(back(CARD,12,true));return e;}
  void say(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);getWindow().setStatusBarColor(0xff000000);getWindow().setNavigationBarColor(0xff000000);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    prefs=getSharedPreferences("error_native",0);myId=prefs.getString("id","");if(myId.isEmpty()){myId=UUID.randomUUID().toString();prefs.edit().putString("id",myId).apply();}
    myName=prefs.getString("name","");
    db=openOrCreateDatabase("error_messages.db",MODE_PRIVATE,null);
    db.execSQL("CREATE TABLE IF NOT EXISTS contacts(id TEXT PRIMARY KEY,name TEXT)");
    db.execSQL("CREATE TABLE IF NOT EXISTS groups(id TEXT PRIMARY KEY,name TEXT)");
    db.execSQL("CREATE TABLE IF NOT EXISTS messages(id TEXT PRIMARY KEY,chat TEXT,sender TEXT,receiver TEXT,grp TEXT,kind TEXT,body TEXT,ts LONG,state TEXT)");
    nearby=Nearby.getConnectionsClient(this);
    render();
  }
  @Override protected void onDestroy(){try{nearby.stopAllEndpoints();nearby.stopAdvertising();nearby.stopDiscovery();}catch(Exception ignored){}if(recorder!=null)try{recorder.release();}catch(Exception ignored){}db.close();super.onDestroy();}
  void saveContact(String id,String name){if(id==null||id.isEmpty()||id.equals(myId))return;android.content.ContentValues v=new android.content.ContentValues();v.put("id",id);v.put("name",name);db.insertWithOnConflict("contacts",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
  String nameOf(String id){try(Cursor c=db.rawQuery("SELECT name FROM contacts WHERE id=?",new String[]{id})){if(c.moveToFirst())return c.getString(0);}return id.length()>8?id.substring(0,8):id;}
  String groupName(String id){try(Cursor c=db.rawQuery("SELECT name FROM groups WHERE id=?",new String[]{id})){if(c.moveToFirst())return c.getString(0);}return "Groupe ERROR";}
  void saveGroup(String id,String name){android.content.ContentValues v=new android.content.ContentValues();v.put("id",id);v.put("name",name);db.insertWithOnConflict("groups",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
  void insertMessage(String id,String chat,String sender,String receiver,String group,String kind,String value,long ts,String state){
    android.content.ContentValues v=new android.content.ContentValues();v.put("id",id);v.put("chat",chat);v.put("sender",sender);v.put("receiver",receiver);v.put("grp",group);v.put("kind",kind);v.put("body",value);v.put("ts",ts);v.put("state",state);
    db.insertWithOnConflict("messages",null,v,SQLiteDatabase.CONFLICT_IGNORE);
  }
  boolean known(String id){try(Cursor c=db.rawQuery("SELECT 1 FROM messages WHERE id=?",new String[]{id})){return c.moveToFirst();}}
  void state(String id,String s){db.execSQL("UPDATE messages SET state=? WHERE id=?",new Object[]{s,id});}
  void screen(){root=col();root.setBackgroundColor(BLACK);setContentView(root);}
  void render(){
    if(myName.isEmpty()){welcome();return;}
    screen();
    if(openChat!=null){chatScreen();return;}
    LinearLayout h=row();pad(h,18,5,12,5);h.setBackgroundColor(0xff000000);
    h.addView(logo(),new LinearLayout.LayoutParams(dp(135),dp(50)));h.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1));
    TextView plus=text("+",26,WHITE,false);plus.setGravity(Gravity.CENTER);h.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(46)));plus.setOnClickListener(v->createGroupDialog());
    add(root,h);
    body=col();stretch(root,body);
    if(tab.equals("chats"))chats();
    else if(tab.equals("near"))discoveryScreen();
    else if(tab.equals("groups"))groupsScreen();
    else if(tab.equals("profile"))profileScreen();
    else settingsScreen();
    LinearLayout nav=row();pad(nav,2,10,2,10);nav.setBackgroundColor(0xff000000);
    navItem(nav,"◉","Chats","chats");navItem(nav,"⌁","Découvrir","near");navItem(nav,"♧","Groupes","groups");navItem(nav,"♙","Profil","profile");navItem(nav,"⚙","Réglages","settings");add(root,nav);
  }
  void navItem(LinearLayout p,String glyph,String name,String key){
    LinearLayout n=col();n.setGravity(Gravity.CENTER);TextView a=text(glyph,23,tab.equals(key)?WHITE:GRAY,false);a.setGravity(Gravity.CENTER);add(n,a);
    TextView b=text(name,10,tab.equals(key)?WHITE:GRAY,tab.equals(key));b.setGravity(Gravity.CENTER);add(n,b);
    p.addView(n,new LinearLayout.LayoutParams(0,dp(52),1));n.setOnClickListener(v->{tab=key;render();});
  }
  void heading(String title,String desc){
    LinearLayout v=col();pad(v,20,20,20,12);add(v,text(title,25,WHITE,true));gap(v,5);add(v,text(desc,13,GRAY,false));add(body,v);
  }
  void welcome(){screen();LinearLayout box=col();box.setGravity(Gravity.CENTER);pad(box,25,20,25,25);stretch(root,box);
    ImageView im=logo();add(box,im);gap(box,30);TextView a=text("Bienvenue sur ERROR",25,WHITE,true);a.setGravity(Gravity.CENTER);add(box,a);
    gap(box,12);TextView d=text("Messagerie Android · Sans Internet à proximité",13,GRAY,false);d.setGravity(Gravity.CENTER);add(box,d);
    gap(box,34);EditText name=field("Votre pseudo");add(box,name);gap(box,12);add(box,button("Commencer →",true,()->{String n=name.getText().toString().trim();if(n.length()<2){name.setError("2 caractères minimum");return;}myName=n;prefs.edit().putString("name",n).apply();render();}));
  }
  void chats(){heading("Discussions","Messages privés et groupes");EditText search=field("Rechercher une conversation...");
    LinearLayout wrap=col();pad(wrap,16,2,16,12);add(wrap,search);add(body,wrap);
    ScrollView s=new ScrollView(this);LinearLayout list=col();pad(list,12,2,12,12);s.addView(list);stretch(body,s);
    populateChats(list,"");search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int c,int f){}public void onTextChanged(CharSequence x,int a,int b,int c){populateChats(list,x.toString());}public void afterTextChanged(Editable e){}});
  }
  String lastText(String chat){try(Cursor c=db.rawQuery("SELECT kind,body FROM messages WHERE chat=? ORDER BY ts DESC LIMIT 1",new String[]{chat})){if(c.moveToFirst()){String k=c.getString(0);return k.equals("text")?c.getString(1):k.equals("image")?"Photo":"Message vocal";}}return "Appuyez pour discuter";}
  void populateChats(LinearLayout list,String q){list.removeAllViews();int count=0;try(Cursor c=db.rawQuery("SELECT id,name FROM groups ORDER BY name",null)){
    while(c.moveToNext()){String id="g:"+c.getString(0),n=c.getString(1);if(n.toLowerCase().contains(q.toLowerCase())){contactRow(list,id,n,true);count++;}}}
    try(Cursor c=db.rawQuery("SELECT id,name FROM contacts ORDER BY name",null)){while(c.moveToNext()){String id=c.getString(0),n=c.getString(1);if(n.toLowerCase().contains(q.toLowerCase())){contactRow(list,id,n,false);count++;}}}
    if(count==0){gap(list,50);TextView empty=text("Aucune discussion.\\nOuvrez Découvrir pour retrouver vos amis.",14,GRAY,false);empty.setGravity(Gravity.CENTER);add(list,empty);}
  }
  void contactRow(LinearLayout list,String id,String name,boolean group){LinearLayout r=row();pad(r,9,15,9,15);
    TextView avatar=text(group?"G":name.substring(0,1).toUpperCase(),19,WHITE,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(back(CARD,60,true));r.addView(avatar,new LinearLayout.LayoutParams(dp(50),dp(50)));
    LinearLayout info=col();pad(info,13,0,0,0);r.addView(info,new LinearLayout.LayoutParams(0,-2,1));add(info,text(name,16,WHITE,true));gap(info,4);
    TextView preview=text(lastText(id),12,GRAY,false);preview.setSingleLine(true);preview.setEllipsize(TextUtils.TruncateAt.END);add(info,preview);
    r.setOnClickListener(v->{openChat=id;render();});add(list,r);
    View line=new View(this);line.setBackgroundColor(EDGE);list.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
  }
  String labelFor(String id){return id.startsWith("g:")?groupName(id.substring(2)):nameOf(id);}
  void chatScreen(){
    String title=labelFor(openChat);boolean grp=openChat.startsWith("g:");
    LinearLayout h=row();pad(h,8,6,12,6);h.setBackgroundColor(0xff000000);
    TextView back=text("‹",35,WHITE,false);back.setGravity(Gravity.CENTER);h.addView(back,new LinearLayout.LayoutParams(dp(45),dp(49)));back.setOnClickListener(v->{openChat=null;render();});
    LinearLayout info=col();h.addView(info,new LinearLayout.LayoutParams(0,-2,1));add(info,text(title,18,WHITE,true));gap(info,3);
    add(info,text(grp?"Groupe à proximité":idToEp.containsKey(openChat)?"● À proximité · connecté":"Hors connexion",12,GRAY,false));add(root,h);
    scroller=new ScrollView(this);LinearLayout messages=col();pad(messages,11,15,11,15);scroller.addView(messages);
    try(Cursor c=db.rawQuery("SELECT id,sender,kind,body,ts,state FROM messages WHERE chat=? ORDER BY ts LIMIT 1000",new String[]{openChat})){while(c.moveToNext())bubble(messages,c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getString(5));}
    if(messages.getChildCount()==0){gap(messages,50);TextView txt=text("Écrivez votre premier message.\\nSans réseau, les messages attendent la reconnexion Nearby.",13,GRAY,false);txt.setGravity(Gravity.CENTER);add(messages,txt);}
    root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));scroller.post(()->scroller.fullScroll(View.FOCUS_DOWN));
    LinearLayout input=row();pad(input,6,7,7,10);input.setBackgroundColor(0xff000000);
    TextView photo=text("+",26,WHITE,true);photo.setGravity(Gravity.CENTER);input.addView(photo,new LinearLayout.LayoutParams(dp(37),dp(47)));photo.setOnClickListener(v->choosePhoto());
    draft=field("Message...");draft.setSingleLine(false);draft.setMaxLines(4);input.addView(draft,new LinearLayout.LayoutParams(0,-2,1));
    TextView mic=text(recording?"■":"●",23,WHITE,false);mic.setGravity(Gravity.CENTER);input.addView(mic,new LinearLayout.LayoutParams(dp(41),dp(46)));mic.setOnClickListener(v->recordVoice());
    TextView send=text("➤",24,WHITE,true);send.setGravity(Gravity.CENTER);input.addView(send,new LinearLayout.LayoutParams(dp(41),dp(46)));
    send.setOnClickListener(v->{String body=draft.getText().toString().trim();if(!body.isEmpty())sendMessage("text",body);});
    add(root,input);
  }
  String datetime(long value){return new SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date(value));}
  void bubble(LinearLayout list,String sender,String kind,String value,long date,String state){
    boolean mine=sender.equals(myId);LinearLayout r=row();r.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);pad(r,3,4,3,4);
    LinearLayout bubble=col();pad(bubble,12,10,12,7);bubble.setBackground(back(mine?0xff27272b:CARD,13,!mine));
    if(kind.equals("text")){TextView t=text(value,15,WHITE,false);t.setMaxWidth(dp(255));add(bubble,t);}
    else if(kind.equals("image"))try{byte[] bytes=android.util.Base64.decode(value,0);Bitmap bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.length);ImageView image=new ImageView(this);image.setImageBitmap(bmp);image.setScaleType(ImageView.ScaleType.CENTER_CROP);bubble.addView(image,new LinearLayout.LayoutParams(dp(212),dp(195)));}catch(Exception e){add(bubble,text("Photo indisponible",14,GRAY,false));}
    else {add(bubble,button("▶  Message vocal",false,()->playAudio(value)));}
    gap(bubble,5);TextView clock=text(datetime(date)+(mine?" · "+state:""),10,GRAY,false);clock.setGravity(Gravity.RIGHT);add(bubble,clock);
    r.addView(bubble,new LinearLayout.LayoutParams(-2,-2));add(list,r);
  }
  void sendMessage(String kind,String value){
    if(openChat==null)return;
    String id=UUID.randomUUID().toString(),grp=openChat.startsWith("g:")?openChat.substring(2):"",to=grp.isEmpty()?openChat:"";
    long ts=System.currentTimeMillis();
    insertMessage(id,openChat,myId,to,grp,kind,value,ts,"pending");
    if(!grp.isEmpty()||idToEp.containsKey(to))sendPacket(id,myId,to,grp,kind,value,ts);
    render();
  }
  void sendPacket(String id,String from,String to,String grp,String kind,String value,long ts){
    ArrayList<String> endpoints=new ArrayList<>();
    if(!grp.isEmpty())endpoints.addAll(idToEp.values());else if(idToEp.containsKey(to))endpoints.add(idToEp.get(to));
    if(endpoints.size()==0)return;
    try{JSONObject j=new JSONObject();j.put("type","message");j.put("id",id);j.put("from",from);j.put("to",to);j.put("group",grp);j.put("groupName",grp.isEmpty()?"":groupName(grp));j.put("kind",kind);j.put("body",value);j.put("ts",ts);
      byte[] bytes=j.toString().getBytes(StandardCharsets.UTF_8);
      if(bytes.length>850000){say("Fichier trop volumineux");return;}
      nearby.sendPayload(endpoints,Payload.fromBytes(bytes)).addOnSuccessListener(x->{state(id,"sent");refresh();}).addOnFailureListener(e->{state(id,"pending");say("Erreur d'envoi : "+e.getMessage());});
    }catch(Exception e){say("Impossible d'envoyer: "+e.getMessage());}
  }
  void flush(){try(Cursor c=db.rawQuery("SELECT id,sender,receiver,grp,kind,body,ts FROM messages WHERE sender=? AND state='pending' ORDER BY ts LIMIT 80",new String[]{myId})){while(c.moveToNext())sendPacket(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getLong(6));}}
  void receive(String endpoint,byte[] bytes){try{
    JSONObject j=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
    if(j.optString("type").equals("ack")){state(j.optString("id"),"delivered");refresh();return;}
    if(!j.optString("type").equals("message"))return;
    String mid=j.optString("id"),from=j.optString("from"),group=j.optString("group"),to=j.optString("to");
    if(!from.equals(epToId.get(endpoint))||known(mid))return;
    if(!group.isEmpty())saveGroup(group,j.optString("groupName","Groupe ERROR"));
    String chat=group.isEmpty()?from:"g:"+group;
    insertMessage(mid,chat,from,to,group,j.optString("kind","text"),j.optString("body"),j.optLong("ts",System.currentTimeMillis()),"received");
    JSONObject ack=new JSONObject();ack.put("type","ack");ack.put("id",mid);
    nearby.sendPayload(endpoint,Payload.fromBytes(ack.toString().getBytes(StandardCharsets.UTF_8)));
    refresh();
  }catch(Exception e){android.util.Log.w("ERROR","Incoming packet",e);}}
  void discoveryScreen(){heading("Découvrir","Discuter sans Internet avec vos amis proches");
    LinearLayout box=col();pad(box,16,14,16,15);box.setBackground(back(CARD,14,true));
    add(box,text("Bluetooth + Wi-Fi Direct",17,WHITE,true));gap(box,9);
    add(box,text("Activez le mode à proximité sur les deux téléphones, puis vérifiez le même code avant d'accepter la connexion.",13,GRAY,false));gap(box,18);
    add(box,button(scanning?"■  Arrêter la découverte":"◉  Activer la découverte",!scanning,()->{if(scanning)stopNear();else permissions();render();}));
    LinearLayout outer=col();pad(outer,16,6,16,5);add(outer,box);add(body,outer);
    ScrollView scroll=new ScrollView(this);LinearLayout list=col();pad(list,18,18,18,12);scroll.addView(list);
    add(list,text(scanning?"Recherche en cours...":"Recherche arrêtée",14,GRAY,false));gap(list,17);
    int cnt=0;for(String ep:new ArrayList<>(epToId.keySet())){String id=epToId.get(ep);if(idToEp.containsKey(id))continue;String n=epToName.get(ep);
      add(list,button("●  "+n+"  ·  Se connecter",false,()->connect(ep)));gap(list,10);cnt++;
    }
    if(cnt==0){add(list,text("Aucun nouvel appareil détecté.",13,GRAY,false));gap(list,15);}
    add(list,text("Amis connectés",18,WHITE,true));gap(list,12);
    if(idToEp.isEmpty())add(list,text("Aucun ami connecté actuellement",13,GRAY,false));
    for(String id:new ArrayList<>(idToEp.keySet())){add(list,button("●  "+nameOf(id)+"  ·  Discuter →",false,()->{openChat=id;render();}));gap(list,10);}
    stretch(body,scroll);
  }
  void permissions(){
    ArrayList<String> ps=new ArrayList<>();
    if(Build.VERSION.SDK_INT>=33){need(ps,Manifest.permission.BLUETOOTH_SCAN);need(ps,Manifest.permission.BLUETOOTH_ADVERTISE);need(ps,Manifest.permission.BLUETOOTH_CONNECT);need(ps,Manifest.permission.NEARBY_WIFI_DEVICES);}
    else if(Build.VERSION.SDK_INT>=31){need(ps,Manifest.permission.BLUETOOTH_SCAN);need(ps,Manifest.permission.BLUETOOTH_ADVERTISE);need(ps,Manifest.permission.BLUETOOTH_CONNECT);need(ps,Manifest.permission.ACCESS_FINE_LOCATION);}
    else if(Build.VERSION.SDK_INT>=29)need(ps,Manifest.permission.ACCESS_FINE_LOCATION);
    else need(ps,Manifest.permission.ACCESS_COARSE_LOCATION);
    if(ps.isEmpty())startNear();else requestPermissions(ps.toArray(new String[0]),110);
  }
  void need(ArrayList<String> p,String s){if(checkSelfPermission(s)!=PackageManager.PERMISSION_GRANTED)p.add(s);}
  @Override public void onRequestPermissionsResult(int code,String[] perms,int[] result){super.onRequestPermissionsResult(code,perms,result);boolean ok=result.length>0;for(int n:result)if(n!=PackageManager.PERMISSION_GRANTED)ok=false;
    if(code==110){if(ok)startNear();else say("Autorisez les appareils à proximité.");render();}
    if(code==111){if(ok)recordVoice();else say("Autorisation du micro requise");}
  }
  String label(){return myName.replace("|"," ")+"|"+myId;}
  void parsePeer(String ep,String name){int p=name.lastIndexOf('|');if(p<0)return;String id=name.substring(p+1),n=name.substring(0,p);if(id.equals(myId))return;epToId.put(ep,id);epToName.put(ep,n);refresh();}
  void startNear(){if(scanning)return;scanning=true;
    nearby.startAdvertising(label(),SERVICE,life,new AdvertisingOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener(e->say("Visibilité : "+e.getMessage()));
    nearby.startDiscovery(SERVICE,found,new DiscoveryOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener(e->say("Recherche : "+e.getMessage()));
    refresh();
  }
  void stopNear(){scanning=false;nearby.stopAdvertising();nearby.stopDiscovery();refresh();}
  void connect(String ep){nearby.requestConnection(label(),ep,life).addOnFailureListener(e->say("Connexion échouée : "+e.getMessage()));}
  final EndpointDiscoveryCallback found=new EndpointDiscoveryCallback(){
    @Override public void onEndpointFound(String id,DiscoveredEndpointInfo info){parsePeer(id,info.getEndpointName());}
    @Override public void onEndpointLost(String ep){epToId.remove(ep);epToName.remove(ep);refresh();}
  };
  final ConnectionLifecycleCallback life=new ConnectionLifecycleCallback(){
    @Override public void onConnectionInitiated(String ep,ConnectionInfo info){parsePeer(ep,info.getEndpointName());
      runOnUiThread(()->new AlertDialog.Builder(MainActivity.this).setTitle("Connexion ERROR")
      .setMessage("Comparez ce code sur les deux appareils :\\n\\n"+info.getAuthenticationDigits())
      .setPositiveButton("Accepter",(d,w)->nearby.acceptConnection(ep,payloads))
      .setNegativeButton("Refuser",(d,w)->nearby.rejectConnection(ep)).setCancelable(false).show());
    }
    @Override public void onConnectionResult(String ep,ConnectionResolution resolution){
      if(resolution.getStatus().isSuccess()){
        String id=epToId.get(ep),name=epToName.get(ep);
        if(id!=null){idToEp.put(id,ep);saveContact(id,name);runOnUiThread(()->say("Connecté : "+name));flush();}
      }else runOnUiThread(()->say("Connexion refusée ou échouée"));
      refresh();
    }
    @Override public void onDisconnected(String ep){String id=epToId.get(ep);if(id!=null)idToEp.remove(id);refresh();}
  };
  final PayloadCallback payloads=new PayloadCallback(){
    @Override public void onPayloadReceived(String ep,Payload p){if(p.getType()==Payload.Type.BYTES){byte[] b=p.asBytes();if(b!=null)receive(ep,b);}}
    @Override public void onPayloadTransferUpdate(String ep,PayloadTransferUpdate u){}
  };
  void refresh(){runOnUiThread(()->{if(!isFinishing())render();});}
  void groupsScreen(){heading("Groupes","Discussions avec plusieurs amis");LinearLayout l=col();pad(l,18,10,18,14);add(l,button("+  Nouveau groupe",true,this::createGroupDialog));gap(l,15);
    try(Cursor c=db.rawQuery("SELECT id,name FROM groups ORDER BY name",null)){while(c.moveToNext()){String id="g:"+c.getString(0),nm=c.getString(1);add(l,button("♧  "+nm+" →",false,()->{openChat=id;render();}));gap(l,10);}}
    add(l,text("Les messages de groupe sont transmis aux amis connectés à proximité.",12,GRAY,false));stretch(body,l);
  }
  void createGroupDialog(){EditText name=field("Nom du groupe");new AlertDialog.Builder(this).setTitle("Créer un groupe").setView(name).setPositiveButton("Créer",(dlg,k)->{String id=UUID.randomUUID().toString(),n=name.getText().toString().trim();saveGroup(id,n.isEmpty()?"Groupe ERROR":n);openChat="g:"+id;render();}).setNegativeButton("Annuler",null).show();}
  void profileScreen(){heading("Mon profil","Votre identité ERROR");LinearLayout l=col();pad(l,19,18,19,15);
    TextView big=text(myName.substring(0,1).toUpperCase(),40,WHITE,true);big.setGravity(Gravity.CENTER);big.setBackground(back(CARD,60,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(100),dp(100));p.gravity=Gravity.CENTER_HORIZONTAL;l.addView(big,p);
    gap(l,22);TextView title=text(myName,25,WHITE,true);title.setGravity(Gravity.CENTER);add(l,title);gap(l,9);
    TextView id=text("Identifiant : "+myId.substring(0,8)+"…",12,GRAY,false);id.setGravity(Gravity.CENTER);add(l,id);gap(l,24);
    add(l,button("Modifier mon pseudo",false,()->{EditText e=field("Pseudo");e.setText(myName);new AlertDialog.Builder(this).setTitle("Mon pseudo").setView(e).setPositiveButton("Enregistrer",(a,b)->{String n=e.getText().toString().trim();if(n.length()>1){myName=n;prefs.edit().putString("name",n).apply();stopNear();render();}}).setNegativeButton("Annuler",null).show();}));
    gap(l,11);add(l,button("Copier mon ID",false,()->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("ERROR ID",myId));say("ID copié");}));
    stretch(body,l);
  }
  void settingsScreen(){heading("Réglages","Options de l'application");LinearLayout l=col();pad(l,18,11,18,16);
    add(l,button("Découverte hors ligne : "+(scanning?"activée":"désactivée"),false,()->{if(scanning)stopNear();else permissions();render();}));gap(l,12);
    add(l,button("Connexion en ligne · configuration requise",false,()->new AlertDialog.Builder(this).setTitle("Mode en ligne").setMessage("Il faut configurer un vrai backend pour contacter des amis éloignés. Le chat hors ligne fonctionne uniquement si une connexion Nearby est disponible.").setPositiveButton("OK",null).show()));gap(l,12);
    add(l,button("Confidentialité",false,()->new AlertDialog.Builder(this).setTitle("Confidentialité").setMessage("Messages enregistrés localement. Cette version de test ne fournit pas de chiffrement de bout en bout audité. Évitez les données sensibles.").setPositiveButton("OK",null).show()));gap(l,12);
    add(l,button("À propos d'ERROR",false,()->new AlertDialog.Builder(this).setTitle("ERROR · Android Native").setMessage("Application Android native, sans site WebView. Test des messages hors ligne entre téléphones Android proches.").setPositiveButton("OK",null).show()));
    stretch(body,l);
  }
  void choosePhoto(){if(openChat==null)return;Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT);in.setType("image/*");in.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(in,222);}
  @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=222||result!=RESULT_OK||data==null)return;
    try{BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(data.getData())){BitmapFactory.decodeStream(in,null,opt);}
      opt.inJustDecodeBounds=false;opt.inSampleSize=1;while(opt.outWidth/opt.inSampleSize>900||opt.outHeight/opt.inSampleSize>900)opt.inSampleSize*=2;
      Bitmap image;try(InputStream in=getContentResolver().openInputStream(data.getData())){image=BitmapFactory.decodeStream(in,null,opt);}
      if(image==null)throw new Exception("Image invalide");
      ByteArrayOutputStream out=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.JPEG,65,out);
      if(out.size()>550000)throw new Exception("Photo trop grande");sendMessage("image",android.util.Base64.encodeToString(out.toByteArray(),2));image.recycle();
    }catch(Exception e){say("Photo impossible : "+e.getMessage());}
  }
  void recordVoice(){if(!recording){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},111);return;}
    try{recorded=new File(getCacheDir(),"v_"+System.currentTimeMillis()+".m4a");recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioEncodingBitRate(32000);recorder.setOutputFile(recorded.getAbsolutePath());recorder.prepare();recorder.start();recording=true;say("Enregistrement... Appuyez à nouveau pour envoyer.");render();}
    catch(Exception e){recording=false;say("Micro : "+e.getMessage());}
   }else{recording=false;try{recorder.stop();recorder.release();recorder=null;byte[] data=new byte[(int)recorded.length()];try(FileInputStream f=new FileInputStream(recorded)){int off=0;while(off<data.length){int n=f.read(data,off,data.length-off);if(n<0)break;off+=n;}}
       if(data.length>530000){say("Vocal trop long (2 minutes max).");render();return;}
       sendMessage("voice",android.util.Base64.encodeToString(data,2));
     }catch(Exception e){say("Vocal trop court");render();}}
  }
  void playAudio(String encoded){try{byte[] data=android.util.Base64.decode(encoded,0);File f=new File(getCacheDir(),"play_"+System.currentTimeMillis()+".m4a");try(FileOutputStream o=new FileOutputStream(f)){o.write(data);}MediaPlayer p=new MediaPlayer();p.setDataSource(f.getAbsolutePath());p.prepare();p.setOnCompletionListener(x->{x.release();f.delete();});p.start();}catch(Exception e){say("Lecture audio impossible");}}
}