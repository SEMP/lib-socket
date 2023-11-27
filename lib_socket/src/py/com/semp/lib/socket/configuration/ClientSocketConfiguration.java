package py.com.semp.lib.socket.configuration;

import static py.com.semp.lib.socket.configuration.Values.VariableNames.*;
import py.com.semp.lib.utilidades.configuration.ConfigurationValues;

public class ClientSocketConfiguration extends ConfigurationValues
{
	public ClientSocketConfiguration()
	{
		super();
	}
	
	@Override
	protected void setRequiredParameters()
	{
		this.addRequiredParameter(String.class, REMOTE_ADDRESS);
		this.addRequiredParameter(Integer.class, REMOTE_PORT);
		this.addRequiredParameter(Integer.class, CONNECTION_TIMEOUT_MS);
		this.addRequiredParameter(Integer.class, READ_TIMEOUT_MS);
		this.addRequiredParameter(Integer.class, WRITE_TIMEOUT_MS);
		this.addOptionalParameter(Integer.class, SOCKET_BUFFER_SIZE_BYTES);
		this.addOptionalParameter(Integer.class, TERMINATION_TIMOUT_MS);
		this.addOptionalParameter(Integer.class, POLL_DELAY_MS);
	}
	
	@Override
	protected void setOptionalParameters()
	{
		this.addOptionalParameter(String.class, LOCAL_ADDRESS);
		this.addOptionalParameter(Integer.class, LOCAL_PORT);
	}
	
	@Override
	protected void setDefaultValues()
	{
		this.setParameter(Integer.class, CONNECTION_TIMEOUT_MS, Values.Defaults.CONNECTION_TIMEOUT_MS);
		this.setParameter(Integer.class, READ_TIMEOUT_MS, Values.Defaults.READ_TIMEOUT_MS);
		this.setParameter(Integer.class, WRITE_TIMEOUT_MS, Values.Defaults.WRITE_TIMEOUT_MS);
		this.setParameter(Integer.class, SOCKET_BUFFER_SIZE_BYTES, Values.Defaults.SOCKET_BUFFER_SIZE_BYTES);
		this.setParameter(Integer.class, TERMINATION_TIMOUT_MS, Values.Defaults.TERMINATION_TIMOUT_MS);
		this.setParameter(Integer.class, POLL_DELAY_MS, Values.Utilities.Defaults.POLL_DELAY_MS);
	}
}