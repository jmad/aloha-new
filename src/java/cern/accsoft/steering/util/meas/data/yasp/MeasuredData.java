package cern.accsoft.steering.util.meas.data.yasp;

import java.util.Collection;

import cern.accsoft.steering.util.meas.data.DataValue;

public interface MeasuredData<T extends DataValue> {

	/**
	 * returns the monitor value for a given key
	 * 
	 * @param key
	 * @return the {@link MonitorValue}
	 */
	public abstract T getMonitorValue(String key);

	/**
	 * @return all available monitorValues
	 */
	public abstract Collection<T> getMonitorValues();
}