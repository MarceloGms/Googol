import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGatewayBrl extends Remote {
    public void AddBrl(IBarrel brl, int id) throws RemoteException;
}
