package py.com.semp.lib.socket.configuration;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

import py.com.semp.lib.socket.drivers.SocketDriver;
import py.com.semp.lib.utilidades.communication.interfaces.DataCommunicator;
import py.com.semp.lib.utilidades.communication.interfaces.DataInterface;
import py.com.semp.lib.utilidades.communication.interfaces.DataReader;
import py.com.semp.lib.utilidades.communication.interfaces.DataTransmitter;
import py.com.semp.lib.utilidades.communication.listeners.ConnectionEventListener;
import py.com.semp.lib.utilidades.communication.listeners.DataListener;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;
import py.com.semp.lib.utilidades.exceptions.ConnectionClosedException;

public class Borrar
{
	private static final ReentrantLock PRINT_LOCK = new ReentrantLock();
	
	public static void main(String[] args) throws CommunicationException
	{
//		DataCommunicator communicator = new SocketChannelDriver();
		DataCommunicator communicator = new SocketDriver();
		ConfigurationValues configurationValues = new ClientSocketConfiguration();
		
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
			PRINT_LOCK.lock();
			System.err.println("Error de envío:");
			System.err.println(dataInterface.getDynamicStringIdentifier());
			System.err.println(instant);
			System.err.println(exception.getMessage());
			PRINT_LOCK.unlock();
		}
		
		@Override
		public void onReceivingError(Instant instant, DataInterface dataInterface, Throwable exception)
		{
			if(exception instanceof ConnectionClosedException)
			{
				return;
			}
			
			PRINT_LOCK.lock();
			System.err.println("Error de Recepción:");
			System.err.println(dataInterface.getDynamicStringIdentifier());
			System.err.println(instant);
			System.err.println(exception.getMessage());
			PRINT_LOCK.unlock();
		}
		
		@Override
		public void onDataSent(Instant instant, DataInterface dataInterface, byte[] data)
		{
			PRINT_LOCK.lock();
			System.out.println("Enviado:");
			System.out.println(dataInterface.getDynamicStringIdentifier());
			System.out.println(instant);
			System.out.println(new String(data));
			PRINT_LOCK.unlock();
		}
		
		@Override
		public void onDataReceived(Instant instant, DataInterface dataInterface, byte[] data)
		{
			PRINT_LOCK.lock();
			System.out.println("Recibido:");
			System.out.println(dataInterface.getDynamicStringIdentifier());
			System.out.println(instant);
			System.out.println(new String(data));	
			PRINT_LOCK.unlock();
			
			if(dataInterface instanceof DataTransmitter)
			{
				DataTransmitter dataTransmitter = (DataTransmitter) dataInterface;
				
				try
				{
					dataTransmitter.sendData(data);
				}
				catch(CommunicationException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	private static class MyConnectionEventListener implements ConnectionEventListener
	{
		@Override
		public void onDisconnect(Instant instant, DataInterface dataInterface)
		{
			PRINT_LOCK.lock();
			System.out.println("Desconectado:");
			System.out.println(dataInterface.getDynamicStringIdentifier());
			System.out.println(instant);
			PRINT_LOCK.unlock();
		}

		@Override
		public void onConnect(Instant instant, DataInterface dataInterface)
		{
			PRINT_LOCK.lock();
			System.out.println("Conectado:");
			System.out.println(dataInterface.getDynamicStringIdentifier());
			System.out.println(instant);
			System.out.println();
			PRINT_LOCK.unlock();
		}

		@Override
		public void onConnectError(Instant instant, DataInterface dataInterface, Throwable throwable)
		{
			PRINT_LOCK.lock();
			System.err.println("Error de conexión:");
			System.err.println(dataInterface.getDynamicStringIdentifier());
			System.err.println(instant);
			System.err.println(throwable.getMessage());
			PRINT_LOCK.unlock();
		}

		@Override
		public void onDisconnectError(Instant instant, DataInterface dataInterface, Throwable throwable)
		{
			PRINT_LOCK.lock();
			System.err.println("Error de desconexión:");
			System.err.println(dataInterface.getDynamicStringIdentifier());
			System.err.println(instant);
			System.err.println(throwable.getMessage());
			PRINT_LOCK.unlock();
		}
	}
}