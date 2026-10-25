package xrz.framework.manager;
public class XrzEinkManager {
    public XrzEinkManager(android.content.Context c) {}
    public abstract static class SuspendCallback extends android.os.ISuspendCallback.Stub {}
    public void registerSuspendCallback(SuspendCallback callback) {}
    public void unRegisterSuspendCallback() {}
}
