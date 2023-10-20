package py.com.semp.lib.socket;

import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import py.com.semp.lib.socket.configuration.SocketConfiguration;
import py.com.semp.lib.socket.configuration.Values;
import py.com.semp.lib.socket.internal.MessageUtil;
import py.com.semp.lib.socket.internal.Messages;
import py.com.semp.lib.utilidades.communication.DataInterface;
import py.com.semp.lib.utilidades.communication.DataReceiver;
import py.com.semp.lib.utilidades.communication.DataTransmitter;
import py.com.semp.lib.utilidades.communication.listeners.ConnectionEventListener;
import py.com.semp.lib.utilidades.communication.listeners.DataListener;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;
import py.com.semp.lib.utilidades.exceptions.CommunicationException;

public class SocketDriver implements DataInterface, DataReceiver, DataTransmitter
{
	private final ReentrantLock lock = new ReentrantLock();
	private Socket socket;
	private SocketConfiguration configurationValues;
	private final CopyOnWriteArraySet<DataListener> dataListeners = new CopyOnWriteArraySet<>();
	private final CopyOnWriteArraySet<ConnectionEventListener> connectionEventListeners = new CopyOnWriteArraySet<>();
	private volatile AtomicBoolean connected = new AtomicBoolean(false);
	private String stringIdentifier;
	
	public SocketDriver() throws CommunicationException
	{
		this(new Socket());
	}
	
	public SocketDriver(Socket socket) throws CommunicationException
	{
		super();
		
		this.setSocket(socket);
	}
	
	private void setSocket(Socket socket) throws CommunicationException
	{
		this.connected.set(false);
		
		if(socket == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("void ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("setSocket(Socket)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
			throw new NullPointerException(errorMessage);
		}
		
		this.lock.lock();
		
		try
		{
			this.socket = socket;
			
			if(this.socket.isConnected() && !this.socket.isClosed())
			{
				this.connected.set(true);
				
				this.setConfiguredSoTimeout();
			}
		}
		finally
		{
			this.lock.unlock();
		}
	}
	
	private void setConfiguredSoTimeout() throws CommunicationException
	{
		Integer readTimeoutMS = this.configurationValues.getValue(Values.VariableNames.READ_TIMEOUT_MS);
		
		if(readTimeoutMS == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("void ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("setConfiguredSoTimeout()");
			
			String errorMessage = MessageUtil.getMessage(Messages.VALUE_SHOULD_NOT_BE_NULL_ERROR, Values.VariableNames.READ_TIMEOUT_MS, methodName.toString());
			
			throw new CommunicationException(errorMessage);
		}
		
		this.setSoTimeout(readTimeoutMS);
	}
	
	private void setSoTimeout(int readTimeoutMS) throws CommunicationException
	{
		try
		{
			this.socket.setSoTimeout(readTimeoutMS);
		}
		catch(SocketException e)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append(this.getClass().getSimpleName());
			methodName.append(" ");
			methodName.append(this.getStringIdentifier());
			
			String errorMessage = MessageUtil.getMessage(Messages.UNABLE_TO_SET_CONFIGURATION_ERROR, "soTimeout", Integer.toString(readTimeoutMS), methodName.toString());
			
			throw new CommunicationException(errorMessage, e);
		}
	}
	
	public Socket getSocket()
	{
		return this.socket;
	}
	
	@Override
	public void sendData(byte[] data) throws CommunicationException
	{
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public SocketDriver addDataListeners(DataListener... listeners)
	{
		if(listeners == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("SocketDriver ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("addDataListeners(DataListener...)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
			throw new NullPointerException(errorMessage);
		}
		
		this.dataListeners.addAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketDriver removeDataListeners(DataListener... listeners)
	{
		if(listeners == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("SocketDriver ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("removeDataListeners(DataListener...)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
			throw new NullPointerException(errorMessage);
		}
		
		this.dataListeners.removeAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketDriver removeAllDataListeners()
	{
		this.dataListeners.clear();
		
		return this;
	}
	
	@Override
	public byte[] readData() throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DataInterface connect() throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DataInterface connect(ConfigurationValues configurationValues) throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DataInterface disconnect() throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DataInterface setConfigurationValues(ConfigurationValues configurationValues) throws CommunicationException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public SocketConfiguration getConfigurationValues()
	{
		return this.configurationValues;
	}
	
	@Override
	public SocketDriver addConnectionEventListeners(ConnectionEventListener... listeners)
	{
		if(listeners == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("SocketDriver ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("addConnectionEventListeners(ConnectionEventListener...)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
			throw new NullPointerException(errorMessage);
		}
		
		this.connectionEventListeners.addAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketDriver removeConnectionEventListeners(ConnectionEventListener... listeners)
	{
		if(listeners == null)
		{
			StringBuilder methodName = new StringBuilder();
			
			methodName.append("SocketDriver ");
			methodName.append(this.getClass().getSimpleName());
			methodName.append("::");
			methodName.append("removeConnectionEventListeners(ConnectionEventListener...)");
			
			String errorMessage = MessageUtil.getMessage(Messages.NULL_VALUES_NOT_ALLOWED_ERROR, methodName.toString());
			
			throw new NullPointerException(errorMessage);
		}
		
		this.connectionEventListeners.removeAll(Set.of(listeners));
		
		return this;
	}
	
	@Override
	public SocketDriver removeAllConnectionEventListeners()
	{
		this.connectionEventListeners.clear();
		
		return this;
	}
	
	@Override
	public String getStringIdentifier()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public boolean isConnected()
	{
		Socket socket = this.getSocket();
		
		if(socket == null)
		{
			return false;
		}
		
		boolean socketConnected = socket.isConnected() && !socket.isClosed();
		
		return this.connected.get() && socketConnected;
	}
}