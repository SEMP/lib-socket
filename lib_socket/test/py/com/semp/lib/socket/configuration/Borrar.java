package py.com.semp.lib.socket.configuration;

import java.time.Instant;

import py.com.semp.lib.socket.SocketDriver;
import py.com.semp.lib.utilidades.communication.interfaces.DataCommunicator;
import py.com.semp.lib.utilidades.communication.interfaces.DataInterface;
import py.com.semp.lib.utilidades.communication.interfaces.DataReader;
import py.com.semp.lib.utilidades.communication.listeners.ConnectionEventListener;
import py.com.semp.lib.utilidades.communication.listeners.DataListener;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;
import py.com.semp.lib.utilidades.exceptions.ConnectionClosedException;

public class Borrar
{
	public static void main(String[] args) throws CommunicationException
	{
//		DataCommunicator communicator = new SocketChannelDriver();
		DataCommunicator communicator = new SocketDriver();
		ConfigurationValues configurationValues = new SocketConfiguration();
		
		configurationValues.setParameter(Values.VariableNames.REMOTE_ADDRESS, "127.0.0.1");
		configurationValues.setParameter(Values.VariableNames.REMOTE_PORT, 8789);
		
//		Logger defaultLogger = LoggerManager.getDefaultLogger();
//		((DefaultLogger) defaultLogger).setDebug(true);
		
		testCommunicator(communicator, configurationValues);
	}

	private static void testCommunicator(DataCommunicator communicator, ConfigurationValues configurationValues) throws CommunicationException
	{
		DataReader dataReader = communicator.getDataReader();
		
		communicator.addDataListeners(new MyDataListener());
		communicator.addConnectionEventListeners(new MyConnectionEventListener());
		
		Thread thread = new Thread(dataReader);
		
		thread.start();
		
		try
		{
			communicator.connect(configurationValues);
			communicator.sendData("hola!!!\n");
		}
		catch(CommunicationException e)
		{
		}
		
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
	
	private static class MyDataListener implements DataListener
	{
		@Override
		public void onSendingError(Instant instant, DataInterface dataInterface, byte[] data, Throwable exception)
		{
			System.err.println("Error de envío:");
			System.err.println(instant);
			System.err.println(exception.getMessage());
		}
		
		@Override
		public void onReceivingError(Instant instant, DataInterface dataInterface, Throwable exception)
		{
			if(exception instanceof ConnectionClosedException)
			{
				return;
			}
			
			System.err.println("Error de Recepción:");
			System.err.println(instant);
			System.err.println(exception.getMessage());
		}
		
		@Override
		public void onDataSent(	Instant instant, DataInterface dataInterface, byte[] data)
		{
			System.out.println("Enviado:");
			System.out.println(instant);
			System.out.println(new String(data));
		}
		
		@Override
		public void onDataReceived(Instant instant, DataInterface dataInterface, byte[] data)
		{
			System.out.println("Recibido");
			System.out.println(instant);
			System.out.println(new String(data));	
		}
	}
	
	private static class MyConnectionEventListener implements ConnectionEventListener
	{
		@Override
		public void onDisconnect(Instant instant, DataInterface dataInterface)
		{
			System.out.println("Desconectado");
			System.out.println(instant);
			System.out.println(dataInterface.getStringIdentifier());
		}

		@Override
		public void onConnect(Instant instant, DataInterface dataInterface)
		{
			System.out.println("Conectado");
			System.out.println(instant);
			System.out.println(dataInterface.getStringIdentifier());
		}

		@Override
		public void onConnectError(Instant instant, DataInterface dataInterface, Throwable throwable)
		{
			System.err.println("Error de conexión:");
			System.err.println(instant);
			System.err.println(dataInterface.getStringIdentifier());
			System.err.println(throwable.getMessage());
		}

		@Override
		public void onDisconnectError(Instant instant, DataInterface dataInterface, Throwable throwable)
		{
			System.err.println("Error de desconexión:");
			System.err.println(instant);
			System.err.println(dataInterface.getStringIdentifier());
			System.err.println(throwable.getMessage());
		}
	}
}
