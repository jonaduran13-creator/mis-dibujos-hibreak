package android.os;
public interface ISuspendCallback extends IInterface {
    android.graphics.Bitmap onPrepareSuspendLogo() throws RemoteException;
    abstract class Stub extends Binder implements ISuspendCallback {
        public IBinder asBinder() { return this; }
    }
}
