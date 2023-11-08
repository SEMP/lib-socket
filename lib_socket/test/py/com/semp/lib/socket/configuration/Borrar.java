package py.com.semp.lib.socket.configuration;

import java.time.Instant;

import py.com.semp.lib.socket.SocketChannelDriver;
import py.com.semp.lib.utilidades.communication.interfaces.DataCommunicator;
import py.com.semp.lib.utilidades.communication.interfaces.DataInterface;
import py.com.semp.lib.utilidades.communication.interfaces.DataReader;
import py.com.semp.lib.utilidades.communication.listeners.DataListener;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;
import py.com.semp.lib.utilidades.exceptions.ConnectionClosedException;

public class Borrar
{
	public static void main(String[] args) throws CommunicationException
	{
		DataCommunicator communicator = new SocketChannelDriver();
		ConfigurationValues configurationValues = new SocketConfiguration();
		
		configurationValues.setParameter(Values.VariableNames.REMOTE_ADDRESS, "127.0.0.1");
		configurationValues.setParameter(Values.VariableNames.REMOTE_PORT, 8789);
		
		testCommunicator(communicator, configurationValues);
	}

	private static void testCommunicator(DataCommunicator communicator, ConfigurationValues configurationValues) throws CommunicationException
	{
		DataReader dataReader = communicator.getDataReader();
		
		communicator.addDataListeners(new Listener());
		
		Thread thread = new Thread(dataReader);
		
		thread.start();
		
		communicator.connect(configurationValues);
		communicator.sendData("hola!!!\n");
		
		try
		{
			thread.join();
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
		}
		
		communicator.disconnect(); //Closing an already closed socket
	}
	
	private static class Listener implements DataListener
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
		public void onDataSent(	Instant instant, DataInterface dataInterface, byte[] data)
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
	}
}
