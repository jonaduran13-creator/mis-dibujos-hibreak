from pathlib import Path
import subprocess
root=Path(__file__).parent
project=root.parent if (root.parent/'src').exists() else root/'dibujos-app'
out=root/'algorithm-tests';out.mkdir(exist_ok=True)
files={
'android/graphics/PointF.java':'''package android.graphics; public class PointF {public float x,y;public PointF(){}public PointF(float x,float y){this.x=x;this.y=y;}}''',
'android/graphics/Color.java':'''package android.graphics;public class Color {public static final int BLACK=0xff000000,WHITE=0xffffffff;public static int red(int n){return n>>16&255;}public static int green(int n){return n>>8&255;}public static int blue(int n){return n&255;}}''',
'android/graphics/Bitmap.java':'''package android.graphics;public class Bitmap {
 public enum Config {ARGB_8888,RGB_565} public enum CompressFormat {PNG} private int w,h;private int[] p;
 private Bitmap(int[] p,int w,int h){this.p=p.clone();this.w=w;this.h=h;}
 public int getWidth(){return w;}public int getHeight(){return h;}
 public void getPixels(int[] dst,int off,int stride,int x,int y,int width,int height){for(int j=0;j<height;j++)System.arraycopy(p,(j+y)*w+x,dst,off+j*stride,width);}
 public Bitmap copy(Config c,boolean mutable){return new Bitmap(p,w,h);}public void recycle(){}
 public boolean compress(CompressFormat f,int quality,java.io.OutputStream stream){try{java.io.DataOutputStream out=new java.io.DataOutputStream(stream);out.writeInt(w);out.writeInt(h);for(int color:p)out.writeInt(color);return true;}catch(Exception e){return false;}}
 public static Bitmap createBitmap(int[] p,int w,int h,Config c){return new Bitmap(p,w,h);}
 public static Bitmap createScaledBitmap(Bitmap src,int w,int h,boolean f){int[] a=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)a[y*w+x]=src.p[(y*src.h/h)*src.w+x*src.w/w];return new Bitmap(a,w,h);}
}''',
'android/graphics/BitmapFactory.java':'''package android.graphics;public class BitmapFactory {public static Bitmap decodeByteArray(byte[] bytes,int off,int len){try{java.io.DataInputStream in=new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes,off,len));int w=in.readInt(),h=in.readInt();int[] p=new int[w*h];for(int i=0;i<p.length;i++)p[i]=in.readInt();return Bitmap.createBitmap(p,w,h,Bitmap.Config.ARGB_8888);}catch(Exception e){return null;}}}''',
'android/media/FaceDetector.java':'''package android.media;import android.graphics.*;public class FaceDetector {public FaceDetector(int w,int h,int n){}public int findFaces(Bitmap b,Face[] f){return 0;}public static class Face{public float confidence(){return 0;}public void getMidPoint(PointF p){}public float eyesDistance(){return 0;}}}''',
'mx/dibujos/kiosko/AlgorithmChecks.java':'''package mx.dibujos.kiosko;
import android.graphics.*;import java.util.*;
public class AlgorithmChecks {
 static void require(boolean b,String name){if(!b)throw new AssertionError(name);System.out.println("PASS "+name);}
 static List<PointF> ring(float rx,float ry,float sweep){List<PointF> p=new ArrayList<>();for(int i=0;i<=100;i++){double t=i*sweep/100;double r=1+.055*Math.sin(t*7)+.025*Math.cos(t*13);p.add(new PointF(300+(float)(rx*r*Math.cos(t)),220+(float)(ry*r*Math.sin(t))));}return p;}
 static List<PointF> polygon(float[][] vertices){List<PointF> p=new ArrayList<>();for(int j=0;j<vertices.length-1;j++)for(int i=0;i<25;i++){float t=i/25f;p.add(new PointF(vertices[j][0]*(1-t)+vertices[j+1][0]*t+(float)Math.sin(i)*2,vertices[j][1]*(1-t)+vertices[j+1][1]*t));}p.add(new PointF(vertices[vertices.length-1][0],vertices[vertices.length-1][1]));return p;}
 static void kind(List<PointF> p,String expected){ShapeSnap.Result r=ShapeSnap.classify(p);String got=r==null?null:r.kind;require(expected==null?got==null:expected.equals(got),(expected==null?"dibujo libre se conserva":expected)+(got==null?"":" ("+got+")"));}
 /** Curva cerrada con temblor, tamaño ~300, empezando a un tercio del recorrido. */
 static List<PointF> curve(java.util.function.DoubleFunction<double[]> f,int seed){List<double[]> v=new ArrayList<>();for(int i=0;i<=200;i++)v.add(f.apply(2*Math.PI*i/200));return wobble(v,seed);}
 static List<PointF> poly(double[][] vs,int seed){List<double[]> v=new ArrayList<>();for(int j=0;j+1<vs.length;j++)for(int i=0;i<30;i++){double t=i/30.0;v.add(new double[]{vs[j][0]*(1-t)+vs[j+1][0]*t,vs[j][1]*(1-t)+vs[j+1][1]*t});}v.add(vs[vs.length-1]);return wobble(v,seed);}
 static List<PointF> wobble(List<double[]> v,int seed){double minx=1e9,maxx=-1e9,miny=1e9,maxy=-1e9;for(double[] p:v){minx=Math.min(minx,p[0]);maxx=Math.max(maxx,p[0]);miny=Math.min(miny,p[1]);maxy=Math.max(maxy,p[1]);}double s=300/Math.max(maxx-minx,maxy-miny);Random r=new Random(seed);List<PointF> out=new ArrayList<>();int n=v.size(),start=n/3;for(int k=0;k<n;k++){double[] p=v.get((start+k)%(n-1));out.add(new PointF((float)(400+p[0]*s+4*Math.sin(k*.2)+r.nextDouble()*2),(float)(300+p[1]*s+4*Math.cos(k*.17)+r.nextDouble()*2)));}out.add(new PointF(out.get(0).x+3,out.get(0).y-2));return out;}
 static double[][] star(int points,double inner){int n=inner>0?points:5;double[][] v=new double[n+1][];for(int i=0;i<=n;i++){double a=Math.toRadians(-90+(inner>0?36:144)*i),r=inner>0&&i%2==1?inner:1;v[i]=new double[]{r*Math.cos(a),r*Math.sin(a)};}return v;}
 static double[][] hexagon(){double[][] v=new double[7][];for(int i=0;i<=6;i++){double a=Math.toRadians(60*i);v[i]=new double[]{Math.cos(a),Math.sin(a)};}return v;}
 static double[] moonInner(double f){double c=.55,r2=Math.hypot(.5-c,Math.sin(Math.toRadians(60))),from=Math.atan2(-Math.sin(Math.toRadians(60)),.5-c)+2*Math.PI,to=Math.atan2(Math.sin(Math.toRadians(60)),.5-c);double t=from-(from-to)*f;return new double[]{c+r2*Math.cos(t),r2*Math.sin(t)};}
 public static void main(String[] args){
  List<Runnable> queued=new ArrayList<>();DrawingHistory history=new DrawingHistory(queued::add);
  Bitmap white=Bitmap.createBitmap(new int[]{Color.WHITE},1,1,Bitmap.Config.ARGB_8888),black=Bitmap.createBitmap(new int[]{Color.BLACK},1,1,Bitmap.Config.ARGB_8888);
  history.push(white.copy(Bitmap.Config.ARGB_8888,false));history.push(black.copy(Bitmap.Config.ARGB_8888,false));
  int[] check=new int[1];history.pop().getPixels(check,0,1,0,0,1,1);require(check[0]==Color.BLACK,"deshacer antes de compresión");
  for(Runnable task:queued)task.run();queued.clear();history.pop().getPixels(check,0,1,0,0,1,1);require(check[0]==Color.WHITE,"deshacer después de compresión");
  for(int i=0;i<45;i++)history.push(white.copy(Bitmap.Config.ARGB_8888,false));for(Runnable task:queued)task.run();queued.clear();
  int count=0;boolean recovered=true;while(history.canUndo()){recovered &= history.pop()!=null;count++;}require(recovered&&count==40,"cuarenta estados recuperables sin perder historial");
  history.push(black.copy(Bitmap.Config.ARGB_8888,false));history.clear();for(Runnable task:queued)task.run();require(!history.canUndo()&&history.pop()==null,"nueva hoja limpia historial de forma segura");
  require(ShapeSnap.recognize(ring(100,92,(float)(Math.PI*2)))!=null,"círculo tembloroso");
  require(ShapeSnap.recognize(ring(100,96,(float)(Math.PI*1.85)))!=null,"círculo con hueco");
  require(ShapeSnap.recognize(ring(150,70,(float)(Math.PI*2)))!=null,"óvalo");
  List<PointF> line=new ArrayList<>();for(int i=0;i<60;i++)line.add(new PointF(i*4,120+(float)Math.sin(i*.5)*8));
  require(ShapeSnap.recognize(line)!=null,"línea temblorosa");
  require(ShapeSnap.recognize(polygon(new float[][]{{30,40},{240,44},{237,190},{32,187},{30,40}}))!=null,"rectángulo irregular");
  require(ShapeSnap.recognize(polygon(new float[][]{{120,20},{240,190},{25,195},{120,20}}))!=null,"triángulo");
  require(ShapeSnap.recognize(ring(100,90,(float)(Math.PI*7)))==null,"garabato conserva vueltas");
  // Figuras nuevas (contornos temblorosos, empezando en un punto cualquiera) y dibujos libres que no se tocan.
  kind(curve(t->new double[]{16*Math.pow(Math.sin(t),3),-(13*Math.cos(t)-5*Math.cos(2*t)-2*Math.cos(3*t)-Math.cos(4*t))},8),"corazón");
  kind(poly(star(10,.42),9),"estrella");
  kind(poly(star(5,0),9),"estrella de un trazo");
  kind(poly(new double[][]{{0,-1},{.7,0},{0,1},{-.7,0},{0,-1}},9),"rombo");
  kind(poly(new double[][]{{0,-1},{.95,.8},{-.95,.8},{0,-1}},9),"triángulo");
  kind(poly(hexagon(),9),"hexágono");
  kind(curve(t->{double s=(t/(2*Math.PI))*441;return s<241?new double[]{Math.cos(Math.toRadians(60+s)),Math.sin(Math.toRadians(60+s))}:moonInner((s-241)/200);},9),"luna");
  kind(curve(t->{double r=.6+.4*Math.abs(Math.cos(2.5*t));return new double[]{r*Math.cos(t),r*Math.sin(t)};},9),null);
  kind(poly(new double[][]{{-1,1},{-1,0},{0,-1},{1,0},{1,1},{-1,1}},9),null);
  kind(poly(new double[][]{{-.3,-1},{.3,-1},{.3,-.3},{1,-.3},{1,.3},{.3,.3},{.3,1},{-.3,1},{-.3,.3},{-1,.3},{-1,-.3},{-.3,-.3},{-.3,-1}},9),null);
  List<PointF> sobra=ring(100,95,(float)(Math.PI*2.25));require(ShapeSnap.classify(sobra)!=null&&ShapeSnap.classify(sobra).kind.equals("círculo"),"círculo que se pasa del inicio");
  List<PointF> diag=new ArrayList<>();for(int i=0;i<50;i++)diag.add(new PointF(i*4,i*4.3f+(float)Math.sin(i)*2));
  List<PointF> rec=ShapeSnap.recognize(diag);PointF a0=rec.get(0),a1=rec.get(rec.size()-1);require(Math.abs(Math.abs(a1.x-a0.x)-Math.abs(a1.y-a0.y))<1,"línea diagonal a 45 grados exactos");
  int w=640,h=320;int[] pixels=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=0xff000000|((x*255/w)<<16)|((y*255/h)<<8)|((x+y)%256);
  Bitmap source=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
  require(FunFilters.apply(source,0)==source,"normal sin conversión");
  for(int mode=1;mode<=4;mode++){
   Bitmap b=FunFilters.apply(source,mode);int[] result=new int[w*h];b.getPixels(result,0,w,0,0,w,h);
   require(b.getWidth()==w&&b.getHeight()==h,"filtro "+mode+" conserva tamaño");
   require(!Arrays.equals(pixels,result),"filtro "+mode+" transforma imagen");
   if(mode==3){boolean same=true;for(int y=0;y<h;y++)for(int x=0;x<w/2;x++)same&=result[y*w+x]==result[y*w+w-1-x];require(same,"espejo simétrico");}
   if(mode==4){
    int[] twice=new int[w*h];FunFilters.apply(b,4).getPixels(twice,0,w,0,0,w,h);
    require(Arrays.equals(pixels,twice),"invertir dos veces regresa la imagen");
    Bitmap blanco=Bitmap.createBitmap(new int[]{Color.WHITE},1,1,Bitmap.Config.ARGB_8888);int[] one=new int[1];
    FunFilters.apply(blanco,4).getPixels(one,0,1,0,0,1,1);require(one[0]==Color.BLACK,"blanco se vuelve negro");
   }
  }
  require(Stamps.NAMES.length==Stamps.COUNT,"un nombre por sello");
  for(int k=0;k<Stamps.COUNT;k++){
   List<List<PointF>> u=Stamps.unit(k);boolean inside=!u.isEmpty();for(List<PointF> st:u){inside&=st.size()>=2;for(PointF q:st)inside&=Math.abs(q.x)<=1.001f&&Math.abs(q.y)<=1.001f;}
   require(inside,"sello "+Stamps.NAMES[k]+" cabe en su caja");
   List<List<PointF>> m=Stamps.make(k,500,300,100);PointF q0=u.get(0).get(0),m0=m.get(0).get(0);
   require(m.size()==u.size()&&Math.abs(m0.x-(500+q0.x*100))<.01f&&Math.abs(m0.y-(300+q0.y*100))<.01f,"sello "+Stamps.NAMES[k]+" se coloca y escala");
  }
  for(int k:new int[]{Stamps.STAR,Stamps.HEART,Stamps.CLOUD}){List<PointF> o=Stamps.unit(k).get(0);PointF f=o.get(0),l=o.get(o.size()-1);require(Math.hypot(f.x-l.x,f.y-l.y)<.01,"contorno de "+Stamps.NAMES[k]+" cerrado");}
  // Película
  Recording peli=new Recording();peli.beginGroup();peli.begin(0,5,false,1000);for(int k=0;k<10;k++)peli.point(k,k,2548,1000+k*10);peli.end(1100);
  peli.beginGroup();peli.begin(2,8,false,1500);peli.point(1,1,2548,1500);peli.point(2,2,2548,1520);peli.end(1520);
  require(peli.strokes().size()==2&&peli.strokes().get(1).gap==400,"película: trazos y pausa");
  peli.dropLast();peli.begin(0,5,false,1600);peli.point(5,5,2548,1600);peli.end(1600);require(peli.strokes().size()==2,"película: figura perfecta reemplaza el trazo");
  try{Recording back=Recording.fromBytes(peli.toBytes());require(back.strokes().size()==2&&back.strokes().get(0).count==10&&back.strokes().get(0).xy[18]==9f&&back.strokes().get(0).time[9]==90,"película: se guarda y se lee igual");
   back.undoGroup();require(back.strokes().size()==1,"película: deshacer quita la acción");back.undoGroup();require(back.isEmpty()&&!back.hasGroups(),"película: deshacer todo");}catch(java.io.IOException e){throw new AssertionError(e);}
  peli.beginGroup();for(int k=0;k<3;k++){peli.begin(0,5,false,2000+k);peli.point(k,k,2548,2000+k);peli.end(2000+k);}peli.undoGroup();require(peli.strokes().size()==2,"película: un sello se deshace entero");
  boolean bad=false;try{Recording.fromBytes(new byte[]{1,2,3,4,5,6,7,8});}catch(java.io.IOException e){bad=true;}require(bad,"película dañada no se acepta");
  List<Recording.Stroke> many=new ArrayList<>();for(int k=0;k<400;k++){Recording.Stroke st=new Recording.Stroke(0,5,false);st.gap=5000;st.add(0,0,0,0);st.add(1,1,0,3000);many.add(st);}
  float[] plan=Recording.schedule(many,25000);require(plan[400]+3000*plan[0]<=25000+1,"película larga se acelera para caber");
  Recording full=new Recording();int rounds=0;while(full.beginGroup()&&rounds<10000){full.begin(0,5,false,rounds);for(int k=0;k<100;k++)full.point(k,k,0,rounds);full.end(rounds);rounds++;}
  require(full.overflowed()&&full.points()>=Recording.MAX_POINTS,"película llena avisa para empezar de nuevo");
  // Laberinto
  for(int seed=1;seed<=30;seed++){Maze m=new Maze(4+seed%11,3+seed%4,seed);require(m.connected()==m.cols*m.rows&&m.pathLength()>0,"laberinto "+seed+" sin celdas aisladas");}
  Maze m=new Maze(8,4,7);m.layout(800,400,10);
  boolean agree=true;for(int c=0;c<m.cols;c++)for(int r=0;r<m.rows;r++){if(c+1<m.cols)agree&=m.crossesWall(m.centerX(c),m.centerY(r),m.centerX(c+1),m.centerY(r))==!m.open(c,r,c+1,r);if(r+1<m.rows)agree&=m.crossesWall(m.centerX(c),m.centerY(r),m.centerX(c),m.centerY(r+1))==!m.open(c,r,c,r+1);}
  require(agree,"laberinto: las paredes bloquean y los pasos abiertos dejan pasar");
  require(m.crossesWall(m.centerX(0),m.centerY(0),m.left-30,m.centerY(0))&&!m.crossesWall(5,5,20,8),"laberinto: no se sale por los bordes; afuera se dibuja libre");
  // Recorrido por el camino abierto (BFS) hasta la meta: debe ganar.
  int[][] prev=new int[m.cols*m.rows][];java.util.ArrayDeque<int[]> q=new java.util.ArrayDeque<>();q.add(new int[]{0,0});prev[0]=new int[]{-1,-1};
  while(!q.isEmpty()){int[] a=q.poll();for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int c=a[0]+d[0],r=a[1]+d[1];if(m.inside(c,r)&&prev[r*m.cols+c]==null&&m.open(a[0],a[1],c,r)){prev[r*m.cols+c]=a;q.add(new int[]{c,r});}}}
  List<int[]> route=new ArrayList<>();int[] at={m.cols-1,m.rows-1};while(at[0]>=0){route.add(0,at);at=prev[at[1]*m.cols+at[0]];}
  Maze cheat=new Maze(8,4,7);cheat.layout(800,400,10);require(!cheat.reach(cheat.centerX(7),cheat.centerY(2),cheat.centerX(7),cheat.centerY(3)),"laberinto: empezar cerca de la meta no cuenta");
  boolean won=false;for(int k=1;k<route.size();k++)won|=m.reach(m.centerX(route.get(k-1)[0]),m.centerY(route.get(k-1)[1]),m.centerX(route.get(k)[0]),m.centerY(route.get(k)[1]));
  require(won&&m.won(),"laberinto: siguiendo el camino se llega a la meta");
  // Canica: empujada en todas direcciones nunca atraviesa una pared ni sale.
  Random rnd=new Random(3);float bx=m.centerX(0),by=m.centerY(0),rad=m.cell*.27f;boolean safe=true;
  for(int k=0;k<4000;k++){float[] o=m.slide(bx,by,bx+(rnd.nextFloat()-.5f)*m.cell*.6f,by+(rnd.nextFloat()-.5f)*m.cell*.6f,rad);
   int c0=m.col(bx),r0=m.row(by),c1=m.col(o[0]),r1=m.row(o[1]);safe&=m.inside(c1,r1)&&(c0==c1&&r0==r1||m.open(c0,r0,c1,r1)||(Math.abs(c1-c0)+Math.abs(r1-r0)==2&&((m.open(c0,r0,c1,r0)&&m.open(c1,r0,c1,r1))||(m.open(c0,r0,c0,r1)&&m.open(c0,r1,c1,r1)))));bx=o[0];by=o[1];}
  require(safe,"canica: no atraviesa paredes");
  // Inclinación desde el acelerómetro: orientación, respuesta y rechazo de temblores.
  for(int rot=0;rot<4;rot++){Tilt t=new Tilt();t.update(2,3,rot,.02f);float[][] expected={{-2,3},{3,2},{2,-3},{-3,-2}};require(Math.abs(t.x()-expected[rot][0])<.001&&Math.abs(t.y()-expected[rot][1])<.001,"canica: ejes del acelerómetro en giro "+rot);}
  Tilt flat=new Tilt();flat.update(0,0,1,.02f);for(int k=0;k<50;k++)flat.update(0,3,1,.02f);require(flat.x()>2.9f&&Math.abs(flat.y())<.001,"canica: una inclinación real mueve aunque el sensor virtual dé cero");
  Tilt noise=new Tilt();noise.update(0,0,1,.02f);for(int k=0;k<100;k++)noise.update(k%2==0?.5f:-.5f,0,1,.02f);require(Math.abs(noise.y())<.1f,"canica: filtra el temblor de la mano");
  Tilt fast=new Tilt(),slow=new Tilt();fast.update(0,0,1,.01f);slow.update(0,0,1,.02f);for(int k=0;k<20;k++)fast.update(3,0,1,.01f);for(int k=0;k<10;k++)slow.update(3,0,1,.02f);require(Math.abs(fast.y()-slow.y())<.001f,"canica: respuesta independiente de frecuencia del sensor");
  float before=flat.x();flat.update(Float.NaN,0,1,.02f);require(Float.isFinite(flat.x())&&flat.x()==before,"canica: ignora lectura dañada");
  // Agitar
  Shake sh=new Shake();sh.feed(0,0,9.8f,0);boolean shook=false;for(int k=1;k<=6;k++)shook|=sh.feed(k%2==0?16:-16,0,9.8f,k*150);require(shook,"agitar: tres sacudidas se reconocen");
  Shake calm=new Shake();calm.feed(0,0,9.8f,0);boolean moved=false;for(int k=1;k<200;k++)moved|=calm.feed((float)Math.sin(k*.05)*9.8f,0,(float)Math.cos(k*.05)*9.8f,k*20);require(!moved,"agitar: girar despacio no cuenta");
  Shake one=new Shake();one.feed(0,0,9.8f,0);boolean same=false;for(int k=1;k<=6;k++)same|=one.feed(16,0,9.8f,k*150);require(!same,"agitar: golpes del mismo lado no cuentan");
 }
}'''
}
for name,content in files.items():
 p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8')
sources=list(out.rglob('*.java'))+[project/'src/mx/dibujos/kiosko/ShapeSnap.java',project/'src/mx/dibujos/kiosko/FunFilters.java',project/'src/mx/dibujos/kiosko/DrawingHistory.java',project/'src/mx/dibujos/kiosko/Stamps.java',project/'src/mx/dibujos/kiosko/Recording.java',project/'src/mx/dibujos/kiosko/Maze.java',project/'src/mx/dibujos/kiosko/Shake.java',project/'src/mx/dibujos/kiosko/Tilt.java']
jdk=next(Path('C:/tmp/hb').glob('jdk-17*'))/'bin'
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-d',str(out/'classes')]+[str(p) for p in sources],check=True)
subprocess.run([str(jdk/'java.exe'),'-cp',str(out/'classes'),'mx.dibujos.kiosko.AlgorithmChecks'],check=True)

