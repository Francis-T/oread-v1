package net.oukranos.oreadv1;

import java.util.List;

import net.oukranos.oreadv1.types.WaterQualityData;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class SensorDataAdapter extends BaseAdapter {
	private static LayoutInflater _layoutInflater = null;
	private List<WaterQualityData> _sensorDataList = null;
	
	public SensorDataAdapter(Activity parent, List<WaterQualityData> data) {
		_layoutInflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		_sensorDataList = data;
		return;
	}

	@Override
	public int getCount() {
		if (_sensorDataList == null) {
			return 0;
		}
		
		return _sensorDataList.size();
	}

	@Override
	public Object getItem(int position) {
		if (isValidIndex(position) == false) {
			return null;
		}
		
		return (_sensorDataList.get(position));
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (isValidIndex(position) == false) {
			return null;
		}
		
		if (parent == null) {
			return null;
		}
		
		View v = convertView;
		if (convertView == null) {
			v = _layoutInflater.inflate(R.layout.sens_data_row, null);
		}
		
		TextView timeText = (TextView) v.findViewById(R.id.timestamp);
		TextView phText = (TextView) v.findViewById(R.id.txt_ph);
		TextView doText = (TextView) v.findViewById(R.id.txt_do2);
		TextView ecText = (TextView) v.findViewById(R.id.txt_econd);
		
		WaterQualityData data = null;
		if (_sensorDataList != null) {
			data = _sensorDataList.get(position);
		}
		
		if (data != null) {
			if (timeText != null) {
				timeText.setText(Long.toString(data.getTimestamp()));
			}
			if (phText != null) {
				phText.setText(Double.toString(data.pH));
			}
			if (doText != null) {
				doText.setText(Double.toString(data.dissolved_oxygen));
			}
			if (ecText != null) {
				ecText.setText(Double.toString(data.conductivity));
			}
		}
		
		return v;
	}

	private boolean isValidIndex(int index) {
		if (_sensorDataList == null) {
			return false;
		}
		
		if ((index < 0) || (index >= _sensorDataList.size())) {
			return false;
		}
		
		return true;
	}
}
