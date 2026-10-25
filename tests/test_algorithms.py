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
 }
}'''
}
for name,content in files.items():
 p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8')
sources=list(out.rglob('*.java'))+[project/'src/mx/dibujos/kiosko/ShapeSnap.java',project/'src/mx/dibujos/kiosko/FunFilters.java',project/'src/mx/dibujos/kiosko/DrawingHistory.java']
jdk=next(Path('C:/tmp/hb').glob('jdk-17*'))/'bin'
subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-d',str(out/'classes')]+[str(p) for p in sources],check=True)
subprocess.run([str(jdk/'java.exe'),'-cp',str(out/'classes'),'mx.dibujos.kiosko.AlgorithmChecks'],check=True)

