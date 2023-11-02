package py.com.semp.lib.socket.configuration;

import py.com.semp.lib.socket.SocketChannelDriver;
import py.com.semp.lib.socket.exceptions.ConnectionClosedException;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;

public class Borrar
{
	public static void main(String[] args) throws CommunicationException
	{
		SocketChannelDriver socket = new SocketChannelDriver();
		
		socket.connect("127.0.0.1", 8789);
		
		socket.sendData("hola!!!\n");
		
		SocketConfiguration configurationValues = socket.getConfigurationValues();
		
		configurationValues.setParameter(Values.VariableNames.READ_TIMEOUT_MS, -1);
		
		while(socket.isConnected())
		{
			try
			{
				byte[] readData = socket.readData();
				
				System.out.print(new String(readData));
				
				Thread.sleep(Values.Constants.POLL_DELAY_MS);
			}
			catch(ConnectionClosedException e)
			{
				System.out.print("Desconectado...");
				break;
			}
			catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				
				throw new CommunicationException(e);
			}
		}
		
		socket.disconnect();
	}
}
