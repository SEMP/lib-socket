package py.com.semp.lib.socket.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static py.com.semp.lib.socket.configuration.Values.VariableNames.CONNECTION_TIMEOUT_MS;
import static py.com.semp.lib.socket.configuration.Values.VariableNames.READ_TIMEOUT_MS;
import static py.com.semp.lib.socket.configuration.Values.VariableNames.REMOTE_ADDRESS;
import static py.com.semp.lib.socket.configuration.Values.VariableNames.REMOTE_PORT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SocketConfigurationTest
{
	private ClientSocketConfiguration socketConfiguration;
	
	@BeforeEach
	public void setUp()
	{
		this.socketConfiguration = new ClientSocketConfiguration();
	}
	
	@Test
	public void testRequiredParametersAreInitiallyUnset()
	{
		assertFalse(this.socketConfiguration.checkRequiredParameters());
		assertNull(this.socketConfiguration.getValue(REMOTE_ADDRESS));
		assertNull(this.socketConfiguration.getValue(REMOTE_PORT));
		assertNotNull(this.socketConfiguration.getValue(CONNECTION_TIMEOUT_MS));
		assertNotNull(this.socketConfiguration.getValue(READ_TIMEOUT_MS));
	}
	
	@Test
	public void testDefaultValuesAreSet()
	{
		assertEquals(Values.Defaults.CONNECTION_TIMEOUT_MS, this.socketConfiguration.getValue(Values.VariableNames.CONNECTION_TIMEOUT_MS));
		assertEquals(Values.Defaults.READ_TIMEOUT_MS, this.socketConfiguration.getValue(Values.VariableNames.READ_TIMEOUT_MS));
	}
	
	@Test
	public void testAfterSettingSomeRequiredParameters()
	{
		this.socketConfiguration.setParameter(REMOTE_ADDRESS, "127.0.0.1");
		assertFalse(this.socketConfiguration.checkRequiredParameters());
	}
	
	@Test
	public void testAfterSettingAllRequiredParameters()
	{
		this.socketConfiguration.setParameter(REMOTE_ADDRESS, "127.0.0.1");
		this.socketConfiguration.setParameter(REMOTE_PORT, 8080);
		this.socketConfiguration.setParameter(CONNECTION_TIMEOUT_MS, 5000);
		this.socketConfiguration.setParameter(READ_TIMEOUT_MS, 1000);
		assertTrue(this.socketConfiguration.checkRequiredParameters());
	}
	
	@Test
	public void testAfterSettingTheNotDefaultRequiredParameters()
	{
		this.socketConfiguration.setParameter(REMOTE_ADDRESS, "127.0.0.1");
		this.socketConfiguration.setParameter(REMOTE_PORT, 8080);
		assertTrue(this.socketConfiguration.checkRequiredParameters());
	}
}