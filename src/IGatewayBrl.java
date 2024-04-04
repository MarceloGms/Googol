import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGatewayBrl extends Remote {
    public void AddBrl(IBarrel brl, int id) throws RemoteException;
    public void rmvBrl(IBarrel brl, int id) throws RemoteException;
    public void BrlMessage(String s) throws RemoteException;
}
