package com.asterion.foundry;
import android.app.*;import android.content.*;import android.content.pm.*;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.net.Uri;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;import java.util.zip.*;
public final class MainActivity extends Activity {
 final int BG=0xff0d1420,PANEL=0xff19263a,RAISED=0xff253650,BORDER=0xff32465e,INK=0xfff1f6fa,MUTED=0xffafc1d2,ACCENT=0xff80e6ca;
 Store store;LinearLayout body;TextView status;Spinner kind;EditText prompt,scale;String source="",page="Create",exportId, nextRecipe, filter=""; final Map<String,String> references = new LinkedHashMap<>();MeshView preview;Handler handler=new Handler();boolean visible;
 static final String KNOWN = "Find registered original";
 static final String HUMAN_REFERENCE = "Character reference candidate";
 String bindingReference, handoffProfile, partBundle;
 String printExportId;double printHeightMm=180;
 String pendingMapParent,pendingMapHash,pendingMapPart,pendingMapChannel;
 Button motionPlaybackButton;
 final String[] kinds={"Route from description","Sword","Pavilion","Tree","Terrain","Image silhouette","Humanoid template","Humanoid with separate tunic","Quadruped template","Articulated dragon","OBJ geometry",ProductionService.ORIGINAL_GLB,ProductionService.NOWORLD_GLB,ProductionService.HULL,ProductionService.PART_HULL,ProductionService.DEPTH,ProductionService.VOLUME,HUMAN_REFERENCE,KNOWN};
 public void onCreate(Bundle b){super.onCreate(b);store=new Store(this);if(b!=null){source=b.getString("source","");exportId=b.getString("exportId");bindingReference=b.getString("bindingReference");handoffProfile=b.getString("handoffProfile");partBundle=b.getString("partBundle");pendingMapParent=b.getString("pendingMapParent");
 pendingMapHash=b.getString("pendingMapHash");pendingMapPart=b.getString("pendingMapPart");
 pendingMapChannel=b.getString("pendingMapChannel");printExportId=b.getString("printExportId");
  printHeightMm=b.getDouble("printHeightMm",180);for(String role:new String[]{"front","right","back"})if(b.containsKey(role))references.put(role,b.getString(role));}int recovering=0;if(!ProductionService.active){store.recover();recovering=store.requeueInterrupted();}getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);show(recovering>0?"Queue":"Create");if(recovering>0)startQueue();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},8);}
 int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
 TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(6),0,dp(6));return t;}
 void label(String v){body.addView(text(v,14,MUTED));}
 Button button(String name,Runnable action){return buttonIn(body,name,action);}
 Button buttonIn(LinearLayout parent,String name,Runnable action){
  Button control=new Button(this);control.setText(name);control.setTextColor(INK);
  control.setTextSize(14);control.setAllCaps(false);
  control.setGravity(Gravity.CENTER_VERTICAL);control.setPadding(dp(16),0,dp(12),0);
  GradientDrawable shape=new GradientDrawable();shape.setColor(PANEL);
  shape.setCornerRadius(dp(14));shape.setStroke(dp(1),BORDER);
  control.setBackground(new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x3380e6ca),shape,null));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));
  p.setMargins(0,dp(5),0,dp(5));parent.addView(control,p);
  control.setOnClickListener(v->{try{action.run();}catch(Exception e){error(e);}});
  return control;
 }
 void section(String title,String subtitle){
  TextView h=text(title,19,INK);
  h.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
  h.setPadding(0,dp(15),0,dp(3));body.addView(h);
  if(subtitle!=null&&!subtitle.isBlank())label(subtitle);
 }
 void actionCard(String title,String subtitle,Runnable onClick){
  LinearLayout card=new LinearLayout(this);card.setOrientation(1);
  card.setPadding(dp(17),dp(11),dp(17),dp(12));
  GradientDrawable d=new GradientDrawable();d.setColor(PANEL);
  d.setCornerRadius(dp(18));d.setStroke(dp(1),BORDER);
  card.setBackground(new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x3380e6ca),d,null));
  TextView heading=text(title,17,INK);
  heading.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
  card.addView(heading);card.addView(text(subtitle,13,MUTED));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
  p.setMargins(0,dp(6),0,dp(6));body.addView(card,p);
  card.setContentDescription(title+". "+subtitle);card.setClickable(true);
  card.setFocusable(true);card.setOnClickListener(v->{try{onClick.run();}catch(Exception e){error(e);}});
 }
 GradientDrawable panelShape(int color,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
 }

void show(String dest){
  if(preview!=null){preview.onPause();preview=null;}
  motionPlaybackButton=null;page=dest;
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  header.setPadding(dp(20),dp(12),dp(18),dp(10));
  LinearLayout brand=new LinearLayout(this);brand.setOrientation(1);
  TextView small=text("ASTERION",11,ACCENT);small.setLetterSpacing(.15f);
  brand.addView(small);
  TextView big=text("3D Foundry",22,INK);
  big.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
  brand.addView(big);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  TextView tools=text("Tools",14,ACCENT);tools.setGravity(Gravity.CENTER);
  tools.setPadding(dp(14),dp(10),dp(14),dp(10));
  tools.setBackground(panelShape(RAISED,12));
  tools.setContentDescription("Specialist studio, Koikatsu and diagnostics");
  tools.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("More tools")
    .setItems(new String[]{"Character and creature studio","Koikatsu source tools","Performance and diagnostics"},
      (dialog,n)->show(new String[]{"Studio","Koikatsu","Performance"}[n])).show());
  header.addView(tools);root.addView(header);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
  body=new LinearLayout(this);body.setOrientation(1);
  body.setPadding(dp(20),0,dp(20),dp(25));scroll.addView(body);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(9),dp(7),dp(9),dp(9));
  nav.setBackgroundColor(0xff132033);
  for(String tab:new String[]{"Create","Projects","Queue","Library"}){
   boolean active=dest.equals(tab);
   TextView target=text(tab,13,active?BG:MUTED);
   target.setGravity(Gravity.CENTER);
   target.setTypeface(android.graphics.Typeface.DEFAULT,active?1:0);
   target.setBackground(panelShape(active?ACCENT:0xff132033,13));
   LinearLayout.LayoutParams weight=new LinearLayout.LayoutParams(0,dp(48),1);
   weight.setMargins(dp(3),0,dp(3),0);nav.addView(target,weight);
   target.setOnClickListener(v->show(tab));
  }
  root.addView(nav);setContentView(root);
  try{
   switch(dest){
    case "Create":create();break;
    case "Projects":projects(false);break;
    case "Queue":projects(true);break;
    case "Studio":studio();break;
    case "Library":library();break;
    case "Koikatsu":kk();break;
    case "Performance":performance();break;
   }
  }catch(Exception e){error(e);}
 }

void create(){
  if(nextRecipe==null){
   section("Create something new","Pick a starting point. All production runs offline; reconstruction quality is reviewed separately.");
   actionCard("From an image","Generate a 3D candidate with the built-in models; refine and inspect the result.",
     ()->openCreator(ProductionService.VOLUME));
   actionCard("Character or creature","Use an illustration to begin anatomy-aware reconstruction.",
     ()->openCreator(HUMAN_REFERENCE));
   actionCard("From a description","Create a supported object or environment recipe.",
     ()->openCreator("Route from description"));
   actionCard("From a 3D file","Preserve the exact original GLB and prepare it for further editing.",
     ()->openCreator(ProductionService.ORIGINAL_GLB));
   section("Other workflows","Precise multi-view input and specialist production methods.");
   button("Use front and side reference views",()->openCreator(ProductionService.HULL));
   button("All creation methods and studios",()->show("Studio"));
   return;
  }
  createForm();
 }
 void openCreator(String recipe){nextRecipe=recipe;show("Create");}
 void createForm() {

  section("New asset","Selected workflow · completely offline production. Choose the source and options.");
  prompt = new EditText(this);
  prompt.setTextColor(INK);
  prompt.setHintTextColor(MUTED);
  prompt.setHint("Describe your asset or name this project");
  body.addView(prompt);
  kind = new Spinner(this);
  kind.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, kinds));
  if (nextRecipe != null) {
   kind.setSelection(Arrays.asList(kinds).indexOf(nextRecipe));
   nextRecipe = null;
  }
  kind.setVisibility(View.GONE);
  body.addView(kind);
  label("Selected workflow · "+kind.getSelectedItem());
  button("Change workflow",()->new AlertDialog.Builder(this).setTitle("Choose production method")
    .setItems(kinds,(dialog,index)->openCreator(kinds[index])).show());
  section("Size","Metres for reconstruction, or a scale multiplier for templates.");
  scale = new EditText(this);
  scale.setText("1.0");
  scale.setTextColor(INK);
  scale.setInputType(8194);
  body.addView(scale);
  
  LinearLayout inputs = new LinearLayout(this);
  inputs.setOrientation(1);
  body.addView(inputs);
  kind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
   public void onItemSelected(AdapterView<?> parent, View view, int position, long rowId) {
    inputs.removeAllViews();
    LinearLayout previous = body;
    body = inputs;
    try {
     String selected = kinds[position];
     if (ProductionService.HULL.equals(selected)) {
      label("Use upright front and right orthographic views of the same subject. An aligned back view is optional.");
      for (int i = 0; i < 3; i++) {
       String role = new String[]{"front", "right", "back"}[i];
       int request = 11 + i;
       Button control = button(viewLabel(role), () -> pick(request, "image/*"));
       control.setTag(role);
      }
     } else if (ProductionService.PART_HULL.equals(selected)) {
      label("Use a ZIP of labelled transparent front and right views on shared canvases. The metre field is the full canvas height.");
      button(partBundleLabel(),()->pick(24,"*/*")).setTag("part-bundle");
     } else if (needsOriginal(selected)) {
      if (KNOWN.equals(selected)) label("Reuse the original GLB registered to these exact image bytes.");
      if (ProductionService.VOLUME.equals(selected) || HUMAN_REFERENCE.equals(selected)) {
       label("Local volumetric reconstruction candidate. Occluded geometry is inferred. Fidelity and rigging are unverified.");
       if (HUMAN_REFERENCE.equals(selected)) label("Check which human joints the reference supports. Low-confidence joints stay unobserved.");
       if (!TripoModels.ready(store.root)) label("All offline models are packaged and prepared automatically on first use.");
      }
      if (ProductionService.DEPTH.equals(selected)) {
       label("Bundled MiDaS estimates the visible surface. Output has a flat backing and relative depth.");
      }
      Button control = button(originalLabel(), () -> pick(1,
              ("OBJ geometry".equals(selected) || originalGlbMode(selected)) ? "*/*" : "image/*"));
      control.setTag("source");
      if (ProductionService.NOWORLD_GLB.equals(selected)) {
       label("Match a self-contained GLB against a declared NoWorld source profile. Unreal import runs separately.");
       button(handoffLabel(), () -> pick(23,"*/*")).setTag("contract");
      }
     } else if (selected.endsWith("template")) {
      label("Generates a rig template. Use multi-view reconstruction for reference-shaped geometry.");
     }
    } finally {
     body = previous;
    }
   }
   public void onNothingSelected(AdapterView<?> parent) {}
  });
  if(kind.getSelectedItemPosition()>=0)
   kind.getOnItemSelectedListener().onItemSelected(kind,null,kind.getSelectedItemPosition(),0);
  CheckBox checkpoint=new CheckBox(this);
  checkpoint.setTextColor(MUTED);
  checkpoint.setText("Advanced · pause after geometry checkpoint");
  body.addView(checkpoint);
  button("Start creation", () -> submit(checkpoint.isChecked()));
  
 }

 private static boolean needsOriginal(String recipe) {
  return KNOWN.equals(recipe) || HUMAN_REFERENCE.equals(recipe) || "Image silhouette".equals(recipe) || "OBJ geometry".equals(recipe)
          || ProductionService.VOLUME.equals(recipe) || ProductionService.DEPTH.equals(recipe) || originalGlbMode(recipe);
 }

 private static boolean originalGlbMode(String recipe) {
  return ProductionService.ORIGINAL_GLB.equals(recipe) || ProductionService.NOWORLD_GLB.equals(recipe);
 }

 private String handoffLabel() {
  return (handoffProfile == null ? "Choose" : "Replace") + " NoWorld JSON profile";
 }

 private String viewLabel(String role) {
  return (references.containsKey(role) ? "Replace " : "Choose ") + role + " view";
 }

 private String originalLabel() {
  return source.isEmpty() ? "Choose original file" : "Replace original file";
 }

 private String partBundleLabel() {
  return partBundle==null?"Choose part reference ZIP":"Replace part reference ZIP";
 }

 private void pick(int code, String type) {
  Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
  intent.setType(type);
  intent.addCategory(Intent.CATEGORY_OPENABLE);
  startActivityForResult(intent, code);
 }

 void submit(boolean pause) {
  try {
   String selected = (String) kind.getSelectedItem();
   boolean humanReference = HUMAN_REFERENCE.equals(selected);
   if (humanReference) selected = ProductionService.VOLUME;
   String name = prompt.getText().toString().trim();
   String description = name.toLowerCase(Locale.ROOT);
   if ("Route from description".equals(selected)) {
    if (description.matches(".*\\btree\\b.*")) selected = "Tree";
    else if (description.matches(".*\\b(terrain|landscape)\\b.*")) selected = "Terrain";
    else if (description.matches(".*\\bpavilion\\b.*")) selected = "Pavilion";
    else if (description.matches(".*\\b(sword|blade)\\b.*")) selected = "Sword";
    else throw new IllegalArgumentException("Choose a production method or describe a supported recipe");

   }
   if (needsOriginal(selected) && source.isEmpty()) {
    throw new IllegalArgumentException("Choose an original file first");
   }
   if (KNOWN.equals(selected) || ProductionService.VOLUME.equals(selected)) {
    File original = new ReferenceRegistry(store.root).find(new File(source).getName());
    if (original != null) {
     enqueue(new JSONObject().put("kind",ProductionService.ORIGINAL_GLB).put("description",name)
             .put("source",original.getAbsolutePath()).put("reference",source)
             .put("scale",1).put("detail",12).put("pauseAfterGeometry",pause));
     return;
    }
    if (KNOWN.equals(selected)) throw new IllegalArgumentException("No original is registered to this exact image");
     // The queue prepares bundled weights without user-supplied model ZIP files.
   }
   if (ProductionService.HULL.equals(selected)
           && (!references.containsKey("front") || !references.containsKey("right"))) {
    throw new IllegalArgumentException("Choose both the front and right reference views");
   }
   if (ProductionService.PART_HULL.equals(selected)&&partBundle==null) {
    throw new IllegalArgumentException("Choose a part reference ZIP first");
   }
   float value = Float.parseFloat(scale.getText().toString());
   if (!Float.isFinite(value) || value < .01 || value > 100) {
    throw new IllegalArgumentException("Use a size between 0.01 and 100");
   }
   JSONObject request = new JSONObject().put("kind", selected).put("description", name)
           .put("scale", value).put("detail", ProductionService.VOLUME.equals(selected) ? 112
                   : ProductionService.HULL.equals(selected)||ProductionService.PART_HULL.equals(selected) ? 40 : 12)
           .put("source", ProductionService.PART_HULL.equals(selected) ? partBundle : needsOriginal(selected) ? source : "")
           .put("pauseAfterGeometry", pause);
   if (humanReference) request.put("human_reference",true);
   if (ProductionService.HULL.equals(selected)) {
    for (Map.Entry<String,String> reference : references.entrySet()) {
     request.put(reference.getKey(), reference.getValue());
    }
   }
   if (ProductionService.NOWORLD_GLB.equals(selected)) {
    if (handoffProfile == null) throw new IllegalArgumentException("Choose the NoWorld JSON profile");
    request.put("contract",handoffProfile);
   }
   enqueue(request);
  } catch (Exception failure) {
   error(failure);
  }
 }

 private void enqueue(JSONObject request) throws Exception {
  store.enqueue(request);
  startQueue();
  show("Queue");
 }

 private void showFormPresets(){
  try{
   List<FormLibrary.Profile> profiles=new FormLibrary(store.root).available("");
   String[] labels=new String[profiles.size()+1];
   labels[0]="Create a new reusable form preset";
   for(int i=0;i<profiles.size();i++)
    labels[i+1]=profiles.get(i).name()+" · revision "+profiles.get(i).revision();
   new AlertDialog.Builder(this).setTitle("Reusable form presets")
    .setItems(labels,(dialog,index)->{
     if(index==0)formEditor("",null);
     else manageForm(profiles.get(index-1));
    }).setNegativeButton("Close",null).show();
  }catch(Exception e){error(e);}
 }
 private void studio() {
  label("Character and creature");
  studioRecipe(HUMAN_REFERENCE);
  studioRecipe(ProductionService.HULL);
  studioRecipe(ProductionService.PART_HULL);
  studioRecipe("Humanoid template");
  studioRecipe("Humanoid with separate tunic");
  studioRecipe("Quadruped template");
  studioRecipe("Articulated dragon");
  label("Objects and environments");
  for (String recipe : new String[]{ProductionService.DEPTH, ProductionService.ORIGINAL_GLB, "OBJ geometry", "Sword", "Pavilion", "Tree", "Terrain"}) {
   studioRecipe(recipe);
  }
 }

 private void studioRecipe(String recipe) {
  button(recipe, () -> {
   nextRecipe = recipe;
   show("Create");
  });
 }

 private void library() throws Exception {
  section("Asset library","Search finished assets and manage source references.");
  button("Register reference and original GLB", () -> pick(21,"image/*"));
  button("Manage form presets",()->showFormPresets());
  EditText search = new EditText(this);
  search.setHint("Search completed assets");
  search.setSingleLine(true);
  search.setText(filter);
  body.addView(search);
  button("Search library", () -> {
   filter = search.getText().toString().trim();
   show("Library");
  });
  String query = filter.toLowerCase(Locale.ROOT);
  int matches = 0;
  for (JSONObject row : store.jobs()) {
   if (!"complete".equals(row.getString("state"))) continue;
   JSONObject request = new JSONObject(row.getString("request"));
   String name = request.optString("description");
   String recipe = request.getString("kind");
   if (!(name + " " + recipe).toLowerCase(Locale.ROOT).contains(query)) continue;
   String id = row.getString("id");
   button(name.isEmpty() ? recipe : name + " · " + recipe, () -> inspect(id));
   matches++;
  }
  if (matches == 0) label("No matching completed assets");
 }
 void startQueue(){try{startForegroundService(new Intent(this,ProductionService.class));}catch(Exception e){error(e);}}
 void projects(boolean queue)throws Exception{
  section(queue?"Production queue":"Your projects",
    queue?"Track active jobs, resume interrupted work, or pause safely at a checkpoint.":
      "Every asset and revision is retained. Open a project to preview, edit or export.");
  if(queue){
   button("Run queued jobs",this::startQueue);
   if(ProductionService.active)button("Pause after current stage",()->
     startService(new Intent(this,ProductionService.class).setAction("pause")));
  }
  button("Refresh",()->show(page));
  ArrayList<JSONObject> jobs=store.jobs();
  if(jobs.isEmpty()){label("No projects yet. Choose Create to begin.");return;}
  int shown=0;
  for(JSONObject job:jobs){
   JSONObject request=new JSONObject(job.getString("request"));
   String id=job.getString("id"),state=job.getString("state");
   String kind=request.optString("kind");
   String name=request.optString("description");
   if(name.isBlank())name=kind;
   LinearLayout card=new LinearLayout(this);card.setOrientation(1);
   card.setPadding(dp(15),dp(10),dp(15),dp(12));
   GradientDrawable surface=new GradientDrawable();surface.setColor(PANEL);
   surface.setCornerRadius(dp(16));surface.setStroke(dp(1),BORDER);
   card.setBackground(surface);
   LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);
   margin.setMargins(0,dp(7),0,dp(7));
   body.addView(card,margin);
   File previewPng=new File(store.project(id),"preview.png");
   File proof=new File(store.project(id),"receipt.json");
   if(previewPng.isFile()&&proof.isFile()){
    try{
     JSONObject evidence=new JSONObject(FilesUtil.readString(proof.toPath()));
     if(evidence.has("thumbnail_sha256")
        &&evidence.getString("thumbnail_sha256").equals(FilesUtil.hash(previewPng))){
      android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
      options.inSampleSize=2;
      android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(
          previewPng.getAbsolutePath(),options);
      if(bitmap!=null){
       ImageView image=new ImageView(this);
       image.setImageBitmap(bitmap);
       image.setScaleType(ImageView.ScaleType.FIT_CENTER);
       image.setBackground(panelShape(RAISED,12));
       image.setContentDescription("Geometry preview for "+name+"; not a validated material render");
       LinearLayout.LayoutParams frame=new LinearLayout.LayoutParams(-1,dp(96));
       frame.setMargins(0,dp(2),0,dp(5));card.addView(image,frame);
      }
     }
    }catch(Exception ignored){ /* Malformed previews never replace project data. */ }
   }
   TextView title=text(name,17,INK);
   title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
   card.addView(title);
   int tone=switch(state){
    case "complete" -> ACCENT;
    case "candidate" -> 0xfff0c87a;
    case "failed" -> 0xffff9b98;
    case "running" -> 0xff8bbaff;
    default -> MUTED;
   };
   String display=switch(state){
    case "complete" -> "Export ready · visual quality unverified";
    case "candidate" -> "Preview only · quality gate not passed";
    case "running" -> "In progress";
    case "queued" -> "Waiting in queue";
    case "paused" -> "Paused · resumable";
    case "failed" -> "Needs attention";
    default -> state;
   };
   TextView badge=text(display,13,tone);
   card.addView(badge);
   card.addView(text(kind+" · "+id.substring(0,8),12,MUTED));
   String message=job.optString("message");
   if(!message.isBlank())card.addView(text(message,12,MUTED));
   if(queue){
    ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    progress.setMax(4);progress.setProgress(Math.max(0,Math.min(4,job.optInt("stage"))));
    card.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
   }
   if(state.equals("paused")||state.equals("failed")){
    buttonIn(card,"Resume job",()->{store.state(id,"queued",job.optInt("stage"),"Resume requested");startQueue();show("Queue");});
   }
   File project=store.project(id);
   if(new File(project,"checkpoint.mesh").isFile()||new File(project,"receipt.json").isFile())
    buttonIn(card,"Open asset",()->inspect(id));
   if(state.equals("complete")){
    buttonIn(card,"Export editable GLB package",()->export(id));
    buttonIn(card,"Repeat using saved settings",()->{
     try{store.enqueue(new JSONObject(job.getString("request")));startQueue();show("Queue");}
     catch(Exception e){error(e);}
    });
   }
   shown++;
  }
  if(shown==0)label("No matching jobs");
 }

 void inspect(String id) {
  try {
   if (preview != null) { preview.onPause(); preview = null; }
   motionPlaybackButton = null;
   File directory = store.project(id);
   if (!new File(directory, "checkpoint.mesh").isFile()) {
    body.removeAllViews();
    JSONObject input = new JSONObject(store.job(id).getString("request"));
    body.addView(text(ProductionService.MATERIAL_VARIANT.equals(input.getString("kind"))
            ? "Material variant receipt" : "Preserved source inventory",28,INK));
    label("Original source preserved. Open the technical report for source and material validation.");
    button("Technical report",()->showTechnicalReport(new File(directory,"receipt.json")));
    if (originalGlbMode(input.getString("kind"))) {
     button("Create a material variant", () -> chooseOriginalMaterial(input.optString("source")));
    }
    button("Export GLB package", () -> export(id));
    button("Back to projects", () -> show("Projects"));
    return;
   }
   Mesh mesh = Mesh.load(new File(store.project(id), "checkpoint.mesh"));
   JSONObject row = store.job(id);
   JSONObject request = new JSONObject(row.getString("request"));
   boolean complete = "complete".equals(row.getString("state"));
   JSONObject receipt = complete ? new JSONObject(FilesUtil.readString(
           new File(directory,"receipt.json").toPath())) : null;
   SkeletalMotion motion = null;
   String motionScript = "";
   if (complete && ProductionService.MOTION.equals(request.getString("kind"))) {
    File script = new File(directory,"motion.foundry.txt");
    if (!receipt.getJSONObject("motion").getString("editable_script_sha256").equals(FilesUtil.hash(script))) {
     throw new IOException("Saved motion failed its checksum");
    }
    motionScript = FilesUtil.readString(script.toPath());
    motion = SkeletalMotion.parse(mesh,motionScript);
   }
   body.removeAllViews();
   body.addView(text("Inspect asset",28,INK));
   preview = new MeshView(this, mesh, new File(directory,"base-color.png"), motion);
   body.addView(preview,new LinearLayout.LayoutParams(-1,dp(330)));
   label("Drag to orbit · " + mesh.vertices.size()/3 + " triangles · " + mesh.bones.size() + " bones");
   LinearLayout views=new LinearLayout(this);
   body.addView(views);
   String[] orientations={"Front","Right","Back","Top"};
   int[] yaw={0,-90,180,0},pitch={0,0,0,85};
   for(int i=0;i<orientations.length;i++){
    final int position=i;
    Button viewButton=buttonIn(views,orientations[i],()->
      preview.view(yaw[position],pitch[position]));
    viewButton.setTextSize(12);viewButton.setGravity(Gravity.CENTER);
    viewButton.setPadding(0,0,0,0);
    LinearLayout.LayoutParams width=new LinearLayout.LayoutParams(0,dp(46),1);
    width.setMargins(dp(2),dp(4),dp(2),dp(4));viewButton.setLayoutParams(width);
   }
   if(mesh.parts().size()>1)button("Show or hide mesh parts",()->chooseVisibleParts(mesh));
   if (motion != null) {
    label(motion.name() + " · " + motion.durationSeconds() + " seconds");
    MeshView animated=preview;
    SeekBar timeline=new SeekBar(this);
    timeline.setMax(Math.max(1,Math.round(motion.durationSeconds()*1000)));
    timeline.setContentDescription("Animation position");
    timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
     public void onProgressChanged(SeekBar bar,int millis,boolean fromUser){if(fromUser)animated.seekMotion(millis);}
     public void onStartTrackingTouch(SeekBar bar){}
     public void onStopTrackingTouch(SeekBar bar){}
    });
    animated.onMotionProgress(timeline::setProgress);
    body.addView(timeline);
    motionPlaybackButton = button("Play animation", () -> motionPlaybackButton.setText(
            preview.toggleMotion() ? "Pause animation" : "Play animation"));
   }
   File qualityEvidenceFile=new File(directory,"receipt.json");
   if(qualityEvidenceFile.isFile()){
    JSONObject qualityEvidence=new JSONObject(FilesUtil.readString(qualityEvidenceFile.toPath()));
    renderQualitySummary(qualityEvidence);
   }
   if(complete)button("Export editable GLB package · NoWorld workflow",()->export(id));
   if(complete)button("3D printing · validate and export STL",()->choosePrintExport(id));
   if(complete&&receipt.has("part_texture_manifest_sha256"))
    button("Inspect saved PBR texture maps",()->inspectPartTextures(id,mesh,receipt));
   LinearLayout editActions=new LinearLayout(this);
   editActions.setOrientation(1);editActions.setVisibility(View.GONE);
   body.addView(editActions);
   button("Edit geometry, materials and animation",()->
     editActions.setVisibility(editActions.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
   LinearLayout outerBody=body;
   body=editActions;
   if (complete) {
    button("Create editable revision", () -> revisionDialog(id, request));
    button("Edit individual part colour", () -> { try { choosePartSurface(id, mesh, receipt.getString("editable_mesh_sha256")); } catch (Exception failure) { error(failure); } });
    button("Edit individual part PBR finish", () -> { try { choosePartFinish(id, mesh, receipt); } catch (Exception failure) { error(failure); } });
    button("Replace a part texture map", () -> choosePartTexture(id, mesh, receipt));
    button("Replace a separate mesh part", () -> { try { choosePartReplacement(id, mesh, receipt); } catch (Exception failure) { error(failure); } });

    if (!mesh.bones.isEmpty()) {
     String baseSha = receipt.getString("editable_mesh_sha256");
     String formScope = FilesUtil.hash(id + "|" + baseSha);
     button("Create a form from saved rules", () -> chooseForm(id, baseSha, formScope, request.optString("description")));
     button("Manage form rules for this base", () -> manageBaseForms(formScope));
     if (ProductionService.FORM.equals(request.getString("kind"))) {
      button("Prefer these settings on the original base", () -> preferForm(request));
     }
     button("Create animation", () -> motionDialog(id,baseSha,null));
     if (ProductionService.ASSEMBLY.equals(request.getString("kind"))) {
      int rootB=receipt.getJSONObject("reference_metrics").getInt("actor_b_root");
      button("Create coordinated animation",()->motionDialog(id,baseSha,
        "clip Coordinated scene\nactor A @0\nactor B @"+rootB+"\nstage A 0 3 idle\nstage B 2 5 idle\n"));
     } else if(mesh.bones.stream().filter(joint->joint.parent<0).count()==1) {
      button("Assemble with another actor",()->choosePartner(id,baseSha));
     }
     if (!mesh.blended() && motion == null) button("Blend skin weights", () -> {
      try {
       enqueue(new JSONObject().put("kind",ProductionService.SKIN_BLEND).put("parent",id)
               .put("parent_mesh_sha256",baseSha).put("scale",1).put("detail",12)
               .put("description",request.optString("description")));
      } catch (Exception failure) { error(failure); }
     });
     if (motion != null) {
      String editable = motionScript;
      button("Edit animation tracks", () -> motionDialog(id,baseSha,editable));
     }
    }
   }
   if (ProductionService.HULL.equals(request.getString("kind")) && request.getInt("detail") < 64) {
    button("Reconstruct at finer resolution", () -> {
     try {
      request.put("detail", 64).put("pauseAfterGeometry", false);
      enqueue(request);
     } catch (Exception failure) { error(failure); }
    });
   }
   body=outerBody;
   File receiptFile = new File(directory, "receipt.json");
   if (receiptFile.isFile())button("Technical validation details",()->showTechnicalReport(receiptFile));
   button("Back to projects", () -> show("Projects"));
  } catch (Exception failure) { error(failure); }
 }

 private void renderQualitySummary(JSONObject receipt){
  section("Quality checks","Engineering checks are separate from artistic and game-engine acceptance.");
  String state=receipt.optString("outcome","candidate");
  boolean candidate=!"complete".equals(state);
  qualityRow("Project status",candidate?"Candidate only · review required":"Asset written · visual review required",
      candidate?0xffefb7a1:ACCENT);
  JSONObject surface=receipt.optJSONObject("surface_audit");
  if(surface!=null){
   int degenerate=surface.optInt("uv_degenerate_triangles",0);
   int out=surface.optInt("uv_outside_01_vertices",0);
   int overlaps=surface.optInt("uv_overlapping_triangle_pairs",0);
   String uvState=surface.optString("uv_overlap","not_checked");
   boolean incomplete=uvState.startsWith("incomplete")||uvState.startsWith("none_detected_in_bounded")
      ||uvState.equals("not_checked");
   String uvMessage;
   if(overlaps>0)uvMessage=overlaps+" possible overlaps · "+
       surface.optInt("uv_cross_part_overlap_pairs",0)+" across separate parts";
   else if(incomplete)uvMessage="Bounded check incomplete · inspect texture seams";
   else uvMessage="No positive-area overlap found in checked UVs";
   if(degenerate>0||out>0)
    uvMessage+=" · "+degenerate+" collapsed triangles · "+out+" out-of-range vertices";
   qualityRow("UV layout",uvMessage,overlaps>0||degenerate>0||out>0||incomplete?0xffedbc7a:ACCENT);
  }else{
   qualityRow("Surface and UVs","No technical surface audit recorded",0xffedbc7a);
  }
  String rig=receipt.optString("rig_validation","not_eligible");
  qualityRow("Rig and motion",rig.equals("not_applicable")?"No skeleton on this asset":
      rig.equals("unrigged")?"Not rigged for animation":
      rig.replace('_',' ')+" · deformation not visually verified",MUTED);
  qualityRow("Reference fidelity","Not visually accepted against the source",0xffedbc7a);
  qualityRow("Engine delivery",receipt.optBoolean("unreal_import_executed",false)?
      "Unreal importer ran; inspect native acceptance separately":
      "NoWorld / Unreal native import not yet accepted",MUTED);
 }
 private void qualityRow(String title,String message,int tone){
  LinearLayout line=new LinearLayout(this);line.setOrientation(1);
  line.setPadding(dp(13),dp(7),dp(13),dp(7));
  line.setBackground(panelShape(PANEL,12));
  LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);
  margin.setMargins(0,dp(3),0,dp(3));
  TextView heading=text(title,13,INK);
  heading.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
  line.addView(heading);
  line.addView(text(message,12,tone));
  body.addView(line,margin);
 }

void showTechnicalReport(File file){
  try{
   new AlertDialog.Builder(this).setTitle("Engineering and validation report")
     .setMessage(FilesUtil.readString(file.toPath()))
     .setPositiveButton("Close",null).show();
  }catch(Exception failure){error(failure);}
 }
 
 private void chooseVisibleParts(Mesh mesh) {
  List<Mesh.Part> parts=mesh.parts();
  String[] names=new String[parts.size()];
  boolean[] checked=new boolean[parts.size()];
  for(int i=0;i<parts.size();i++){
   names[i]=parts.get(i).name();
   checked[i]=preview.partVisible(i);
  }
  MeshView current=preview;
  new AlertDialog.Builder(this).setTitle("Mesh parts")
    .setMultiChoiceItems(names,checked,(dialog,part,visible)->current.setPartVisible(part,visible))
    .setPositiveButton("Done",null).show();
 }

 private void choosePrintExport(String id) {
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);
  form.setPadding(dp(18),dp(4),dp(18),dp(4));
  form.addView(text("3D printing is a separate static-geometry export. "
    +"NoWorld GLB, original meshes, skin, morphs and animations stay unchanged.",14,INK));
  EditText heightField=formField(form,"Printed height in mm (10–600)",String.format(Locale.ROOT,
      "%.0f",printHeightMm));
  form.addView(text("Strict export rejects open, non-manifold, inverted and collapsed geometry. "
    +"It does not yet generate support structures, min-wall thickness, drains, hollowing "
    +"or colour prints. Fine hair, horns, layered wings and delicate details need "
    +"physical engineering and a slicer review.",13,MUTED));
  AlertDialog dialog=new AlertDialog.Builder(this)
    .setTitle("3D print · geometry preflight")
    .setView(form).setNegativeButton("Cancel",null)
    .setPositiveButton("Validate mesh",null).create();
  dialog.setOnShowListener(ignored ->
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(clicked -> {
     try {
      double height=Double.parseDouble(heightField.getText().toString().trim());
      PrintExport.Options options=new PrintExport.Options(height);
      dialog.dismiss();
      Toast.makeText(this,"Checking complete geometry without changing the project",Toast.LENGTH_LONG).show();
      new Thread(()->{
       try {
        if(!"complete".equals(store.job(id).getString("state")))
          throw new IOException("Only completed editable projects can be sent to printing");
        File folder=store.project(id),meshFile=new File(folder,"checkpoint.mesh");
        JSONObject receipt=new JSONObject(FilesUtil.readString(new File(folder,"receipt.json").toPath()));
        if(!FilesUtil.hash(meshFile).equals(receipt.getString("editable_mesh_sha256")))
          throw new IOException("The saved print geometry failed its project checksum");
        Mesh mesh=Mesh.load(meshFile);
        PrintExport.Audit audit=PrintExport.inspect(mesh,options,()->{});
        audit.requireGeometryGate();
        runOnUiThread(()->new AlertDialog.Builder(this)
          .setTitle("Technical geometry gate passed")
          .setMessage(audit.summary()+"\n\nThis does not certify printability. "
             +"Minimum walls, interpenetrations, trapped resin, support needs, "
             +"and printer-specific limits remain unverified. Use a slicer and "
             +"print a physical test for intricate models.")
          .setNegativeButton("Cancel",null)
          .setPositiveButton("Save STL", (d,which)->{
            printExportId=id;
            printHeightMm=height;
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("model/stl");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE,"Foundry-"+id.substring(0,8)+"-print.stl");
            startActivityForResult(intent,26);
          }).show());
       } catch(Exception failure) {runOnUiThread(()->error(failure));}
      },"FoundryPrintPreflight").start();
     } catch(Exception failure) {error(failure);}
    }));
  dialog.show();
 }

 private void export(String id) {
  exportId = id;
  Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
  intent.setType("application/zip");
  intent.addCategory(Intent.CATEGORY_OPENABLE);
  intent.putExtra(Intent.EXTRA_TITLE, "Foundry-" + id.substring(0,8) + ".zip");
  startActivityForResult(intent,2);
 }

 private void chooseOriginalMaterial(String sourcePath) {
  new Thread(() -> {
   try {
    List<String> names = OriginalMaterialVariant.editableNames(new File(sourcePath));
    if (names.isEmpty()) throw new IOException("No uniquely named editable materials were found in this GLB");
    runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Choose a source material")
            .setItems(names.toArray(new String[0]),(dialog,item) ->
                new AlertDialog.Builder(this).setTitle(names.get(item))
                    .setItems(new String[]{"Tint original colour","Edit metallic and roughness"},
                        (choice,option)->{
                            if(option==0)tintDialog(sourcePath,names.get(item));
                            else originalPbrDialog(sourcePath,names.get(item));
                        }).show()).show());
   } catch (Exception failure) { runOnUiThread(() -> error(failure)); }
  }).start();
 }

 private void originalPbrDialog(String sourcePath,String material) {
  try{
   OriginalMaterialVariant.FactorResult current=
       OriginalMaterialVariant.factors(new File(sourcePath),material);
   LinearLayout fields=new LinearLayout(this);
   fields.setPadding(dp(20),0,dp(20),0);
   fields.setOrientation(1);
   fields.addView(text("Adjusts the selected original GLB material without rewriting geometry, original textures, morphs, animation or skin. A separate revision is created.",14,INK));
   EditText metallic=formField(fields,"Metallic (0.0–1.0)",Double.toString(current.metallic()));
   EditText roughness=formField(fields,"Roughness (0.0–1.0)",Double.toString(current.roughness()));
   AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Original PBR finish")
      .setView(fields).setNegativeButton("Cancel",null)
      .setPositiveButton("Create revision",null).create();
   dialog.setOnShowListener(ignored->
     dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      try{
       double metal=Double.parseDouble(metallic.getText().toString().trim());
       double rough=Double.parseDouble(roughness.getText().toString().trim());
       new PartMaterials.Finish(metal,rough);
       enqueue(new JSONObject().put("kind",ProductionService.MATERIAL_VARIANT)
         .put("material_mode","pbr_factors").put("source",sourcePath)
         .put("material_name",material)
         .put("metallic",metal).put("roughness",rough)
         .put("description",material+" · original PBR revision"));
       dialog.dismiss();
      }catch(Exception error){error(error);}
     }));
   dialog.show();
  }catch(Exception error){error(error);}
 }

 private void tintDialog(String sourcePath, String material) {
  EditText tint = new EditText(this);
  tint.setSingleLine(true);
  tint.setHint("Tint #RRGGBB");
  AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Tint " + material)
          .setMessage("Multiplies the chosen material’s colour. The original rig, morphs, textures and animations are preserved.")
          .setView(tint).setNegativeButton("Cancel",null).setPositiveButton("Create variant",null).create();
  dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   try {
    String rgb = tint.getText().toString().trim();
    if (!rgb.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use #RRGGBB for tint");
    enqueue(new JSONObject().put("kind",ProductionService.MATERIAL_VARIANT).put("source",sourcePath)
            .put("material_name",material).put("tint",Color.parseColor(rgb) & 0xffffff).put("description",material));
    dialog.dismiss();
   } catch (Exception failure) { error(failure); }
  }));
  dialog.show();
 }

 private void chooseForm(String id, String baseSha, String formScope, String baseName) {
  try {
   List<FormLibrary.Profile> forms = new FormLibrary(store.root).available(formScope);
   if (forms.isEmpty()) throw new IOException("Save shared form rules in Library or rules for this base first");
   String[] choices = forms.stream().map(profile -> profile.name() + " · v" + profile.revision()
           + (profile.baseScope().isEmpty() ? " · shared" : " · this base")).toArray(String[]::new);
   new AlertDialog.Builder(this).setTitle("Choose a saved form").setItems(choices, (dialog, selected) -> {
    try {
     FormLibrary.Profile profile = forms.get(selected);
     FormLibrary.Rule rule = profile.rule();
     JSONObject snapshot = new JSONObject().put("name",profile.name()).put("scope",profile.baseScope())
             .put("version",profile.revision()).put("x",rule.x()).put("y",rule.y()).put("z",rule.z())
             .put("tone",rule.tone()).put("strength",rule.strength()).put("protect_head",rule.preserveHead());
     enqueue(new JSONObject().put("kind",ProductionService.FORM).put("parent",id)
             .put("parent_mesh_sha256",baseSha).put("form",snapshot).put("scale",1).put("detail",12)
             .put("description",baseName.isBlank() ? profile.name() : baseName + " · " + profile.name()));
    } catch (Exception failure) { error(failure); }
   }).show();
  } catch (Exception failure) { error(failure); }
 }

 private void preferForm(JSONObject request) {
  try {
   FormLibrary.Profile form = ProductionService.formFrom(request.getJSONObject("form"));
   new FormLibrary(store.root).save(form.name(),ProductionService.formScope(request),form.rule());
   Toast.makeText(this,form.name() + " will use these settings on the original base",Toast.LENGTH_LONG).show();
  } catch (Exception failure) { error(failure); }
 }

 private void manageBaseForms(String scope) {
  try {
   List<FormLibrary.Profile> profiles = new FormLibrary(store.root).available(scope).stream()
           .filter(form -> scope.equals(form.baseScope())).collect(java.util.stream.Collectors.toList());
   String[] choices = new String[profiles.size()+1];
   choices[0] = "Save new base-specific rules";
   for (int i = 0; i < profiles.size(); i++) choices[i+1] = profiles.get(i).name();
   new AlertDialog.Builder(this).setTitle("Form rules for this base").setItems(choices,(dialog, item) -> {
    if (item == 0) formEditor(scope,null);
    else manageForm(profiles.get(item-1));
   }).show();
  } catch (Exception failure) { error(failure); }
 }

 private void manageForm(FormLibrary.Profile current) {
  new AlertDialog.Builder(this).setTitle(current.name()).setItems(new String[]{"Edit latest rules", "Rule history"},
          (dialog,item) -> {
   if (item == 0) {
    formEditor(current.baseScope(),current);
    return;
   }
   try {
    List<FormLibrary.Profile> history = new FormLibrary(store.root).history(current.name(),current.baseScope());
    String[] versions = history.stream().map(profile -> "Version " + profile.revision()).toArray(String[]::new);
    new AlertDialog.Builder(this).setTitle("Load rules from a version")
            .setItems(versions,(versionsDialog,index) -> formEditor(current.baseScope(),history.get(index))).show();
   } catch (Exception failure) { error(failure); }
  }).show();
 }

 private void motionDialog(String id, String baseSha, String editable) {
  boolean tracks = editable != null;
  EditText input = new EditText(this);
  if (tracks) {
   input.setText(editable);
   input.setMaxLines(12);
  } else {
   input.setSingleLine(true);
   input.setHint("Idle, walk in place, tail wag, dragon flight or perch");
  }
  AlertDialog dialog = new AlertDialog.Builder(this).setTitle(tracks ? "Edit animation tracks" : "Create animation")
          .setMessage(tracks ? "Edit local joint keyframes and optional contact checks."
                  : "Choose a movement that fits the rig. Flight, landing and perch require the articulated dragon rig.")
          .setView(input).setNegativeButton("Cancel",null).setPositiveButton("Create clip",null).create();
  dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   try {
    String value = input.getText().toString().trim();
    int limit = tracks ? SkeletalMotion.MAX_SCRIPT_CHARS : 80;
    if (value.isEmpty() || value.length() > limit) throw new IllegalArgumentException("Enter a supported animation");
    enqueue(new JSONObject().put("kind",ProductionService.MOTION).put("parent",id)
            .put("parent_mesh_sha256",baseSha).put(tracks ? "motion_script" : "motion_intent",value)
            .put("description",tracks ? "Edited animation" : value).put("scale",1).put("detail",12));
    dialog.dismiss();
   } catch (Exception failure) { error(failure); }
  }));
  dialog.show();
 }

 private void choosePartner(String id,String baseSha){
  try{
   List<String> ids=new ArrayList<>(),hashes=new ArrayList<>(),names=new ArrayList<>();
   for(JSONObject job:store.jobs()){
    String candidate=job.getString("id");
    if(candidate.equals(id)||!job.getString("state").equals("complete"))continue;
    JSONObject request=new JSONObject(job.getString("request"));
    if(ProductionService.ASSEMBLY.equals(request.getString("kind")))continue;
    File receiptFile=new File(store.project(candidate),"receipt.json");
    if(!receiptFile.isFile())continue;
    JSONObject proof=new JSONObject(FilesUtil.readString(receiptFile.toPath()));
    if(proof.optInt("bones")<1||proof.optInt("rig_roots",1)!=1||proof.has("texture_sha256"))continue;
    String checksum=proof.optString("editable_mesh_sha256");
    if(checksum.isEmpty())continue;
    ids.add(candidate);hashes.add(checksum);
    names.add(request.optString("description").isBlank()?request.getString("kind"):request.getString("description"));
   }
   if(ids.isEmpty())throw new IOException("Create another editable rig before assembling actors");
   new AlertDialog.Builder(this).setTitle("Choose the second actor").setItems(names.toArray(new String[0]),(dialog,item)->{
    try{
     enqueue(new JSONObject().put("kind",ProductionService.ASSEMBLY).put("parent",id)
       .put("parent_mesh_sha256",baseSha).put("partner",ids.get(item)).put("partner_mesh_sha256",hashes.get(item))
       .put("description","Two-actor scene with "+names.get(item)).put("scale",1).put("detail",12));
    }catch(Exception failure){error(failure);}
   }).show();
  }catch(Exception failure){error(failure);}
 }

 private void formEditor(String scope, FormLibrary.Profile existing) {
  FormLibrary.Rule rule = existing == null ? new FormLibrary.Rule(1,1,1,0xffffff,0,true) : existing.rule();
  LinearLayout fields = new LinearLayout(this);
  fields.setOrientation(1);
  fields.setPadding(dp(20),0,dp(20),0);
  TextView context = text(scope.isEmpty() ? "Reusable on compatible rigged meshes" : "Overrides the shared rule for this exact base mesh",14,INK);
  fields.addView(context);
  EditText name = formField(fields,"Form name",existing == null ? "" : existing.name());
  if (existing != null) name.setEnabled(false);
  EditText width = formField(fields,"Width multiplier",Float.toString(rule.x()));
  EditText height = formField(fields,"Height multiplier",Float.toString(rule.y()));
  EditText depth = formField(fields,"Depth multiplier",Float.toString(rule.z()));
  EditText tone = formField(fields,"Tint #RRGGBB",String.format(Locale.ROOT,"#%06X",rule.tone()));
  EditText strength = formField(fields,"Tint strength (0–1)",Float.toString(rule.strength()));
  CheckBox protect = new CheckBox(this);
  protect.setText("Keep colours on head and face joints");
  protect.setChecked(rule.preserveHead());
  fields.addView(protect);
  ScrollView scroll = new ScrollView(this);
  scroll.addView(fields);
  AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Save form rules").setView(scroll)
          .setNegativeButton("Cancel",null).setPositiveButton("Save version",null).create();
  dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
   try {
    String rgb = tone.getText().toString().trim();
    if (!rgb.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use #RRGGBB for tint");
    FormLibrary.Rule updated = new FormLibrary.Rule(Float.parseFloat(width.getText().toString()),
            Float.parseFloat(height.getText().toString()),Float.parseFloat(depth.getText().toString()),
            Color.parseColor(rgb) & 0xffffff,Float.parseFloat(strength.getText().toString()),protect.isChecked());
    FormLibrary.Profile saved = new FormLibrary(store.root).save(name.getText().toString(),scope,updated);
    dialog.dismiss();
    Toast.makeText(this,saved.name() + " rules saved · v" + saved.revision(),Toast.LENGTH_LONG).show();
    if ("Library".equals(page)) show("Library");
   } catch (Exception failure) { error(failure); }
  }));
  dialog.show();
 }

 private EditText formField(LinearLayout fields, String caption, String value) {
  fields.addView(text(caption,14,INK));
  EditText entry = new EditText(this);
  entry.setSingleLine(true);
  entry.setText(value);
  fields.addView(entry);
  return entry;
 }


 private void inspectPartTextures(String id,Mesh mesh,JSONObject receipt){
  try{
   PartTextureMaps maps=PartTextureMaps.load(store.project(id),mesh,
      receipt.getString("part_texture_manifest_sha256"));
   List<Mesh.Part> parts=new ArrayList<>();
   List<String> names=new ArrayList<>();
   for(Mesh.Part p:mesh.parts()){
    int count=0;
    for(String channel:PartTextureMaps.CHANNELS)
     if(maps.get(p.id(),channel)!=null)count++;
    if(count==0)continue;
    parts.add(p);
    names.add(p.name()+" · "+count+" texture"+(count==1?"":"s"));
   }
   if(parts.isEmpty())throw new IOException("No saved texture maps in this project");
   new AlertDialog.Builder(this).setTitle("Project PBR textures")
    .setItems(names.toArray(new String[0]),(dialog,index)->{
     Mesh.Part part=parts.get(index);
     List<String> choices=new ArrayList<>();
     List<String> channels=new ArrayList<>();
     for(String channel:PartTextureMaps.CHANNELS)
      if(maps.get(part.id(),channel)!=null){
       channels.add(channel);
       choices.add(switch(channel){
        case "base_color" -> "Base colour and alpha";
        case "metallic_roughness" -> "Metallic and roughness";
        case "normal" -> "Normal / surface relief";
        default -> "Emission";
       });
      }
     new AlertDialog.Builder(this).setTitle(part.name()+" textures")
      .setItems(choices.toArray(new String[0]),(d,choice)->
         previewPbrImage(part,channels.get(choice),maps.get(part.id(),channels.get(choice))))
      .setNegativeButton("Close",null).show();
    }).setNegativeButton("Close",null).show();
  }catch(Exception failure){error(failure);}
 }
 private void previewPbrImage(Mesh.Part part,String channel,byte[] png){
  try{
   PartTextureMaps.validatePng(png);
   android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();
   opts.inJustDecodeBounds=true;
   android.graphics.BitmapFactory.decodeByteArray(png,0,png.length,opts);
   int max=Math.max(opts.outWidth,opts.outHeight);
   if(max<=0)throw new IOException("PNG decoder rejected saved texture");
   opts.inJustDecodeBounds=false;
   opts.inSampleSize=1;
   while(max/opts.inSampleSize>512)opts.inSampleSize*=2;
   android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(png,0,png.length,opts);
   if(bitmap==null)throw new IOException("Saved map cannot be displayed");
   LinearLayout contents=new LinearLayout(this);contents.setOrientation(1);
   contents.setPadding(dp(20),dp(4),dp(20),dp(4));
   ImageView image=new ImageView(this);
   image.setImageBitmap(bitmap);
   image.setScaleType(ImageView.ScaleType.FIT_CENTER);
   image.setContentDescription(part.name()+" "+channel+" texture");
   contents.addView(image,new LinearLayout.LayoutParams(-1,dp(250)));
   contents.addView(text(PartTextureMaps.pngWidth(png)+" × "+PartTextureMaps.pngHeight(png)
       +" PNG · colour and material response require model preview validation",13,MUTED));
   new AlertDialog.Builder(this).setTitle(part.name()+" · "+channel)
    .setView(contents).setPositiveButton("Close",null).show();
  }catch(Exception failure){error(failure);}
 }

 private void choosePartTexture(String id,Mesh mesh,JSONObject receipt){
  List<Mesh.Part> parts=mesh.parts();
  String[] names=parts.stream().map(p->p.name()+" · "+p.role()).toArray(String[]::new);
  new AlertDialog.Builder(this).setTitle("Choose an editable part")
    .setItems(names,(dialog,partIndex)->{
     Mesh.Part part=parts.get(partIndex);
     String[] titles={"Base colour / alpha","Normal / surface detail",
        "Metallic and roughness","Emission / glow"};
     new AlertDialog.Builder(this).setTitle("Texture for "+part.name())
      .setMessage("Select a PNG up to 4096 px and 12 MiB. A new revision retains original geometry, rig, other textures and sibling parts. Visual fidelity still needs review.")
      .setItems(titles,(pickDialog,index)->{
       pendingMapParent=id;
       pendingMapHash=receipt.optString("editable_mesh_sha256");
       pendingMapPart=part.id();
       pendingMapChannel=PartTextureMaps.CHANNELS[index];
       pick(25,"image/png");
      }).setNegativeButton("Cancel",null).show();
    }).setNegativeButton("Cancel",null).show();
 }

 private void choosePartFinish(String id, Mesh mesh, JSONObject receipt) throws Exception {
  File saved = receipt.has("part_materials_sha256")
      ? new File(store.project(id), PartMaterials.FILE) : null;
  if (saved != null && !receipt.getString("part_materials_sha256").equals(FilesUtil.hash(saved)))
   throw new IOException("Saved PBR layer failed its checksum");
  PartMaterials materials = PartMaterials.load(saved, mesh);
  List<Mesh.Part> parts = mesh.parts();
  String[] labels = parts.stream().map(part -> part.name() + " · " + part.role()).toArray(String[]::new);
  new AlertDialog.Builder(this).setTitle("Select a part for PBR finish")
    .setItems(labels, (dialog, selected) -> partFinishDialog(id,
        receipt.optString("editable_mesh_sha256"), parts.get(selected),
        materials.forPart(parts.get(selected)))).show();
 }

 private void partFinishDialog(String id, String parentHash, Mesh.Part part, PartMaterials.Finish finish) {
  LinearLayout fields = new LinearLayout(this);
  fields.setOrientation(1);
  fields.setPadding(dp(20),0,dp(20),0);
  fields.addView(text("Independent material on " + part.name()
      + ". Geometry, existing colours, textures and skin remain unchanged. "
      + "The GLB carries these PBR factors; phone preview lighting is not yet validated.",14,INK));
  EditText metallic = formField(fields, "Metallic (0.0–1.0)", Double.toString(finish.metallic()));
  EditText roughness = formField(fields, "Roughness (0.0–1.0)", Double.toString(finish.roughness()));
  AlertDialog dialog = new AlertDialog.Builder(this)
    .setTitle("Part PBR finish")
    .setView(fields).setNegativeButton("Cancel", null)
    .setPositiveButton("Create separate revision", null).create();
  dialog.setOnShowListener(ignored ->
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      try {
        double metal = Double.parseDouble(metallic.getText().toString().trim());
        double rough = Double.parseDouble(roughness.getText().toString().trim());
        new PartMaterials.Finish(metal, rough);
        enqueue(new JSONObject().put("kind",ProductionService.PART_MATERIAL)
          .put("parent",id).put("parent_mesh_sha256",parentHash)
          .put("part_id",part.id()).put("metallic",metal).put("roughness",rough)
          .put("scale",1).put("detail",12)
          .put("description",part.name() + " PBR finish revision"));
        dialog.dismiss();
      } catch(Exception failure) { error(failure); }
    }));
  dialog.show();
 }


 private void choosePartReplacement(String id, Mesh target, JSONObject receipt) {
  List<Mesh.Part> parts = target.parts();
  String[] labels = parts.stream().map(part -> part.name() + " · " + part.role()).toArray(String[]::new);
  new AlertDialog.Builder(this).setTitle("Choose the part to replace")
    .setItems(labels, (dialog, index) -> choosePartDonor(id, target,
        receipt, parts.get(index))).show();
 }

 private void choosePartDonor(String id, Mesh target, JSONObject receipt, Mesh.Part targetPart) {
  try {
   List<String> ids = new ArrayList<>(), hashes = new ArrayList<>(), names = new ArrayList<>();
   for (JSONObject job : store.jobs()) {
    String candidate = job.getString("id");
    if (id.equals(candidate) || !"complete".equals(job.getString("state"))) continue;
    File proofFile = new File(store.project(candidate),"receipt.json");
    if (!proofFile.isFile()) continue;
    JSONObject proof = new JSONObject(FilesUtil.readString(proofFile.toPath()));
    String hash = proof.optString("editable_mesh_sha256");
    if (hash.isEmpty() || proof.optInt("bones",0) != target.bones.size()
        || !proof.optString("texture_sha256").equals(receipt.optString("texture_sha256"))) continue;
    ids.add(candidate);
    hashes.add(hash);
    JSONObject request = new JSONObject(job.getString("request"));
    String description = request.optString("description");
    names.add(description.isBlank() ? request.getString("kind") : description);
   }
   if (ids.isEmpty()) throw new IOException("No compatible editable donor projects with the same texture atlas");
   new AlertDialog.Builder(this).setTitle("Choose a source project")
     .setItems(names.toArray(new String[0]), (dialog, index) -> {
       try {
        String donorId = ids.get(index);
        File donorFile = new File(store.project(donorId),"checkpoint.mesh");
        if (!hashes.get(index).equals(FilesUtil.hash(donorFile)))
         throw new IOException("Donor geometry checksum mismatch");
        Mesh donor = Mesh.load(donorFile);
        if (!PartReplacement.sameRig(target,donor))
         throw new IOException("Donor requires an identical ordered skeleton and rest pose");
        List<Mesh.Part> candidates = donor.parts().stream()
           .filter(part -> part.role().equals(targetPart.role())).toList();
        if (candidates.isEmpty()) throw new IOException("Donor has no corresponding part role");
        String[] choices = candidates.stream().map(part -> part.name() + " · " + part.id())
            .toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("Transfer part geometry")
          .setMessage("Keeps target part ID and finishes. Requires the same rig and atlas. "
              + "Fit, clipping and animation deformation are not visually verified.")
          .setItems(choices, (second, selected) -> {
            try {
              enqueue(new JSONObject().put("kind",ProductionService.PART_REPLACEMENT)
                .put("parent",id)
                .put("parent_mesh_sha256",receipt.getString("editable_mesh_sha256"))
                .put("donor",donorId).put("donor_mesh_sha256",hashes.get(index))
                .put("part_id",targetPart.id()).put("donor_part_id",candidates.get(selected).id())
                .put("scale",1).put("detail",12)
                .put("description","Replace "+targetPart.name()+" using "+names.get(index)));
            } catch (Exception failure) { error(failure); }
          }).show();
       } catch(Exception failure) { error(failure); }
     }).show();
  } catch(Exception failure) { error(failure); }
 }

 private void choosePartSurface(String id, Mesh mesh, String parentHash) {
  List<Mesh.Part> parts = mesh.parts();
  String[] labels = parts.stream().map(part -> part.name() + " · " + part.role()).toArray(String[]::new);
  new AlertDialog.Builder(this).setTitle("Select a separate mesh part")
    .setItems(labels, (dialog, selected) ->
        partSurfaceDialog(id, parentHash, parts.get(selected))).show();
 }

 private void partSurfaceDialog(String id, String parentHash, Mesh.Part part) {
  LinearLayout fields = new LinearLayout(this);
  fields.setOrientation(1);
  fields.setPadding(dp(20),0,dp(20),0);
  fields.addView(text("Only " + part.name() + " (" + part.id() + ") changes. Other parts, the rig, UVs and source textures remain intact.",14,INK));
  EditText colour = formField(fields,"Target colour #RRGGBB","#FFFFFF");
  EditText strength = formField(fields,"Blend strength (0.0–1.0)","1.0");
  AlertDialog dialog = new AlertDialog.Builder(this)
    .setTitle("Part surface revision")
    .setView(fields).setNegativeButton("Cancel",null)
    .setPositiveButton("Create separate revision",null).create();
  dialog.setOnShowListener(ignored ->
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
      try {
        String hex=colour.getText().toString().trim();
        if(!hex.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use a colour in #RRGGBB format");
        float amount=Float.parseFloat(strength.getText().toString());
        if(!Float.isFinite(amount)||amount<0||amount>1)throw new IllegalArgumentException("Strength must be from 0 to 1");
        enqueue(new JSONObject().put("kind",ProductionService.PART_SURFACE)
          .put("parent",id).put("parent_mesh_sha256",parentHash)
          .put("part_id",part.id()).put("part_rgb",Color.parseColor(hex) & 0xffffff)
          .put("part_strength",(double)amount)
          .put("scale",1).put("detail",12)
          .put("description",part.name() + " colour revision"));
        dialog.dismiss();
      }catch(Exception failure){error(failure);}
    }));
  dialog.show();
 }

 private void revisionDialog(String id, JSONObject original) {
  LinearLayout fields = new LinearLayout(this);
  fields.setOrientation(1);
  fields.setPadding(dp(20),0,dp(20),0);
  EditText[] axes = new EditText[3];
  for (int i=0; i<axes.length; i++) {
   fields.addView(text(new String[]{"Width ×", "Height ×", "Depth ×"}[i],14,INK));
   axes[i] = new EditText(this);
   axes[i].setInputType(8194);
   axes[i].setText("1.0");
   fields.addView(axes[i]);
  }
  EditText color = new EditText(this);
  color.setSingleLine(true);
  color.setHint("Optional colour #RRGGBB");
  fields.addView(color);
  AlertDialog dialog = new AlertDialog.Builder(this).setTitle("New project revision")
          .setView(fields).setNegativeButton("Cancel",null).setPositiveButton("Save revision",null).create();
  dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   try {
    JSONArray multipliers = new JSONArray();
    for (EditText axis : axes) {
     float value = Float.parseFloat(axis.getText().toString());
     if (!Float.isFinite(value) || value < .05 || value > 20) {
      throw new IllegalArgumentException("Each multiplier must be between 0.05 and 20");
     }
     multipliers.put(value);
    }
    JSONObject request = new JSONObject(original.toString()).put("kind",ProductionService.REVISION)
            .put("parent",id).put("axes",multipliers).put("pauseAfterGeometry",false)
            .put("parent_mesh_sha256",FilesUtil.hash(new File(store.project(id),"checkpoint.mesh")));
    String paint = color.getText().toString().trim();
    request.remove("color");
    if (!paint.isEmpty()) {
     if (!paint.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use #RRGGBB for colour");
     request.put("color",Color.parseColor(paint));
    }
    enqueue(request);
    dialog.dismiss();
   } catch (Exception failure) { error(failure); }
  }));
  dialog.show();
 }
 void kk(){boolean installed;try{getPackageManager().getPackageInfo("com.asterion.kk.test",0);installed=true;}catch(Exception e){installed=false;}label(installed?"Existing Asterion KK installation detected and preserved.":"Existing KK package was not visible to this app.");label("Card preservation is available. Source-accurate mesh extraction, zipmod resolution and Android runtime IPC are NOT implemented. Cards contain parameters and dependency references; they are not self-contained geometry.");button("Preserve original character card",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/png");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,3);});label("No screenshot reconstruction is substituted for source data. No Windows plugin is executed by this app.");}
 void performance(){ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();getSystemService(ActivityManager.class).getMemoryInfo(m);PowerManager pm=getSystemService(PowerManager.class);label(String.format(Locale.ROOT,"Available RAM: %.2f GiB\nFree app storage: %.2f GiB\nThermal status: %d\nWorker: %s",m.availMem/1073741824.0,store.root.getUsableSpace()/1073741824.0,pm.getCurrentThermalStatus(),ProductionService.active?"running":"idle"));label("Geometry: Android CPU / ART\nPreview: OpenGL ES GPU rendering\nVulkan compute: not implemented\nQNN inference: not implemented\nAI depth: bundled MiDaS small (native CPU)");label("Offline 3D models: "+(TripoModels.ready(store.root) ? "Prepared in private app storage" : "Bundled in APK · unpacked on first reconstruction"));
   label(store.memory());label("One worker · pause on severe thermal state, low RAM or low storage · immutable source cache · stage checkpoints. Android foreground-service time limits still apply. No control of other applications’ memory.");if (Build.VERSION.SDK_INT >= 30) {
   label("Recent Foundry process exits");
   List<ApplicationExitInfo> exits = getSystemService(ActivityManager.class)
           .getHistoricalProcessExitReasons(getPackageName(),0,5);
   if (exits.isEmpty()) label("Android recorded no recent exits for Foundry");
   for (ApplicationExitInfo exit : exits) {
    String reason = switch (exit.getReason()) {
     case ApplicationExitInfo.REASON_CRASH -> "Java crash";
     case ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash";
     case ApplicationExitInfo.REASON_ANR -> "Application not responding";
     case ApplicationExitInfo.REASON_LOW_MEMORY -> "Low memory";
     case ApplicationExitInfo.REASON_USER_REQUESTED -> "Stopped by user";
     default -> "Android reason " + exit.getReason();
    };
    label(new java.util.Date(exit.getTimestamp()) + " · " + reason);
   }
  }
  button("Refresh performance",()->show("Performance"));
 }
 @Override protected void onSaveInstanceState(Bundle state) {
  super.onSaveInstanceState(state);
  state.putString("source",source);
  state.putString("exportId",exportId);
  state.putString("bindingReference",bindingReference);
  state.putString("handoffProfile",handoffProfile);
  state.putString("partBundle",partBundle);
  state.putString("printExportId",printExportId);
  state.putDouble("printHeightMm",printHeightMm);
  state.putString("pendingMapParent",pendingMapParent);
  state.putString("pendingMapHash",pendingMapHash);
  state.putString("pendingMapPart",pendingMapPart);
  state.putString("pendingMapChannel",pendingMapChannel);
  for (Map.Entry<String,String> entry : references.entrySet()) state.putString(entry.getKey(),entry.getValue());
 }

 @Override protected void onActivityResult(int code,int result,Intent data) {
  super.onActivityResult(code,result,data);
  if (result != RESULT_OK || data == null || data.getData() == null) return;
  Uri uri = data.getData();
  String exporting = exportId;
  // Large source geometry is streamed to immutable storage, not held as a bitmap.
  // Only self-contained GLBs receive the 1 GiB budget; reference images,
  // mod cards, general files and part textures retain the 64 MiB cap.
  boolean largeOriginal = code == 22 || (code == 1 && kind != null
      && originalGlbMode((String)kind.getSelectedItem()));
  new Thread(() -> {
   try {
    if (code == 21 || code == 22) {
     File original;
     try (InputStream input = getContentResolver().openInputStream(uri)) {
      original = new SourceFiles(store.root).preserve(input,
          largeOriginal ? SourceFiles.MODEL_IMPORT_BYTES : SourceFiles.DEFAULT_IMPORT_BYTES);
     }
     if (code == 21) {
      SourceFiles.decode(original,128);
      runOnUiThread(() -> {
       bindingReference = original.getAbsolutePath();
       pick(22,"*/*");
      });
     } else {
      String referencePath = bindingReference;
      if (referencePath == null) throw new IOException("Select the reference image again");
      new ReferenceRegistry(store.root).bind(new File(referencePath),original);
      runOnUiThread(() -> {
       bindingReference = null;
       Toast.makeText(this,"Reference registered to original GLB",Toast.LENGTH_LONG).show();
      });
     }
     return;
    }
    if (code == 2) {
     try (OutputStream output = getContentResolver().openOutputStream(uri)) {
      if (output == null) throw new IOException("Cannot open the selected destination");
      ProjectPackage.write(store, exporting, output);
     }
     runOnUiThread(() -> Toast.makeText(this,"Editable package exported",Toast.LENGTH_LONG).show());
     return;
    }
    if (code != 1 && code != 3 && code != 23 && code != 24 && code != 25 && (code < 11 || code > 13)) return;
    File original;
    try (InputStream input = getContentResolver().openInputStream(uri)) {
     original = new SourceFiles(store.root).preserve(input,
         largeOriginal ? SourceFiles.MODEL_IMPORT_BYTES : SourceFiles.DEFAULT_IMPORT_BYTES);
    }
    runOnUiThread(() -> {
     if (code == 25) {
      try {
       String parent=pendingMapParent,hash=pendingMapHash,part=pendingMapPart,channel=pendingMapChannel;
       if(parent==null||hash==null||part==null||channel==null)
        throw new IOException("Part texture selection was interrupted. Select the target again.");
       pendingMapParent=null;pendingMapHash=null;pendingMapPart=null;pendingMapChannel=null;
       enqueue(new JSONObject().put("kind",ProductionService.PART_TEXTURE)
         .put("parent",parent).put("parent_mesh_sha256",hash)
         .put("part_id",part).put("map_channel",channel)
         .put("source",original.getAbsolutePath())
         .put("scale",1).put("detail",12)
         .put("description",part+" · "+channel+" texture revision"));
      }catch(Exception failure){error(failure);}
      return;
     }
     if (code == 24) {
      partBundle=original.getAbsolutePath();
      Button control=(Button)body.findViewWithTag("part-bundle");
      if(control!=null)control.setText(partBundleLabel());
      return;
     }
     if (code == 23) {
      handoffProfile = original.getAbsolutePath();
      Button control = (Button) body.findViewWithTag("contract");
      if (control != null) control.setText(handoffLabel());
      return;
     }
     if (code == 3) {
      new AlertDialog.Builder(this).setTitle("Original card preserved")
              .setMessage("SHA-256: " + original.getName() + "\nGeometry extraction remains unavailable.")
              .setPositiveButton("OK",null).show();
      return;
     }
     String role = code == 1 ? "source" : new String[]{"front","right","back"}[code-11];
     if (code == 1) source = original.getAbsolutePath();
     else references.put(role,original.getAbsolutePath());
     Button control = (Button) body.findViewWithTag(role);
     if (control != null) control.setText(code == 1 ? originalLabel() : viewLabel(role));
    });
   } catch (Exception failure) {
    runOnUiThread(() -> error(failure));
   }
  }).start();
 }
 void error(Exception e){new AlertDialog.Builder(this).setTitle("Operation could not finish").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("OK",null).show();}
 @Override protected void onResume(){super.onResume();if(preview!=null)preview.onResume();}
 @Override protected void onPause(){if(preview!=null)preview.onPause();if(motionPlaybackButton!=null)motionPlaybackButton.setText("Play animation");super.onPause();}
}
