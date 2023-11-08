package py.com.semp.lib.socket.configuration;

import java.time.Instant;

import py.com.semp.lib.socket.SocketChannelDriver;
import py.com.semp.lib.utilidades.communication.interfaces.DataInterface;
import py.com.semp.lib.utilidades.communication.interfaces.DataReader;
import py.com.semp.lib.utilidades.communication.listeners.DataListener;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;
import py.com.semp.lib.utilidades.exceptions.ConnectionClosedException;

public class Borrar
{
	public static void main(String[] args) throws CommunicationException
	{
		SocketChannelDriver socket = new SocketChannelDriver();
		
		DataReader dataReader = socket.getDataReader();
		
		socket.addDataListeners(new DataListener()
		{
			@Override
			public void onSendingError(Instant instant, DataInterface dataInterface, byte[] data, Throwable exception)
			{
				System.err.println("Sending error:");
				System.err.println(instant);
				System.err.println(exception.getMessage());
			}
			
			@Override
			public void onReceivingError(Instant instant, DataInterface dataInterface, Throwable exception)
			{
				if(exception instanceof ConnectionClosedException)
				{
					System.out.println("desconectado: " + dataInterface.getStringIdentifier());
					
					return;
				}
				
				System.err.println("Receiving error:");
				System.err.println(instant);
				System.err.println(exception.getMessage());
			}
			
			@Override
			public void onDataSent(Instant instant, DataInterface dataInterface, byte[] data)
			{
				System.out.println("Sent:");
				System.out.println(instant);
				System.out.println(new String(data));
			}
			
			@Override
			public void onDataReceived(Instant instant, DataInterface dataInterface, byte[] data)
			{
				System.out.println("Received");
				System.out.println(instant);
				System.out.println(new String(data));	
			}
		});
		
		Thread thread = new Thread(dataReader);
		
		thread.start();
		
		socket.connect("127.0.0.1", 8789);
		
		socket.sendData("hola!!!\n");
		
		SocketConfiguration configurationValues = socket.getConfigurationValues();
		
		configurationValues.setParameter(Values.VariableNames.READ_TIMEOUT_MS, -1);
		
		try
		{
			thread.join();
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
		}
		
		socket.disconnect(); //Closing an already closed socket
	}
}
