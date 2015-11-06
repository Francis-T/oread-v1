package net.oukranos.oreadv1.fragments;

import java.util.ArrayList;
import java.util.List;

import net.oukranos.oreadv1.R;
import net.oukranos.oreadv1.interfaces.OreadServiceApi;
import net.oukranos.oreadv1.types.Status;
import net.oukranos.oreadv1.util.OreadLogger;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class FragmentGuidedCalibration extends Fragment implements OnItemClickListener {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private View _viewRef = null;
	private static final String steps[] = new String[] { 
		"Step 0: Clear Existing Calibration Data",
		"Step 1: Conductivity Dry Calibration",
		"Step 2: DO2 Air Calibration",
		"Step 3: Low-Point Solution Calibration",
		"Step 4: High-Point Solution Calibration" };
	private List<CalibrationStep> _stepList = null;
	
	private OreadServiceApi _serviceAPI = null;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		if (container == null) {
			// We have different layouts, and in one of them this
			// fragment's containing frame doesn't exist. The fragment
			// may still be created from its saved state, but there is
			// no reason to try to create its view hierarchy because it
			// won't be displayed. Note this is not needed -- we could
			// just run the code below, where we would create and return
			// the view hierarchy; it would just never be used.
			return null;
		}
		_viewRef = inflater.inflate(R.layout.frag_guided_calibration, container, false);
		
		_stepList = this.getCalibrationSteps();
		
		ArrayAdapter<String> adapter 
			= new ArrayAdapter<String>(this.getActivity(), 
					android.R.layout.simple_list_item_1, steps);
		ListView listView = (ListView) _viewRef.findViewById(R.id.list_calib_steps);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(this);
		
		return _viewRef;
	}

	@Override
	public void onDestroyView() {
		if (this._serviceAPI != null) {
			_serviceAPI = null;
		}
		
		super.onDestroyView();
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		return;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		final CalibrationStep step = _stepList.get(position);
		final View itemView = v;
		
		if (step.isCompleted()) {
			new AlertDialog.Builder(v.getContext())
		    .setTitle(step.getName() + ": " + step.getDescription())
		    .setMessage("[DONE]")
		    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int whichButton) {
		        	return;
		        }
		    }).show();
			return;
		}
		
		new AlertDialog.Builder(v.getContext())
	    .setTitle(step.getName() + ": " + step.getDescription())
	    .setMessage(step.getInstructions())
	    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int whichButton) {
	        	TaskSet taskSet = step.getTaskSet();
	        	
	        	if (taskSet != null) {
	        		taskSet.executeTasks();
	        	}
	        	
	        	step.setCompleted(true);
	        	
				/* Set the background color depending on the selections on the dialog box */
	        	itemView.setBackgroundColor(0xff2dc12c);
	        	
	        	return;
	        }
	    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int whichButton) {
	        	return;
	        }
	    }).show();
		
		return;
	}
	
	/********************/
	/** Public Methods **/
	/********************/
	public OreadServiceApi getServiceHandle() {
		return this._serviceAPI;
	}
	
	public void setServiceHandle(OreadServiceApi api) {
		this._serviceAPI = api;
		return;
	}
	
	private List<CalibrationStep> getCalibrationSteps() {
		List<CalibrationStep> stepList = new ArrayList<CalibrationStep>();
		
		CalibrationStep step = null;
		
		step = new CalibrationStep("Step 0", 
				"Clear Existing Calibration Data",
				"Clears existing calibration data on the sensor circuits. " + 
				"This step must be done prior to sending any calibration " +
				"commands so that they will be processed properly. Press " +
				"'Ok' to clear calibration data for all sensor circuits. ");
		step.setTaskSet(_taskSet[0]);
		stepList.add(step);
		
		step = new CalibrationStep("Step 1", 
				"Conductivity Dry Calibration",
				"Calibrate the conductivity probe to air. Ensure that the " +
				"sensor probe is dry as this can affect calibration. Press " +
				"'Ok' to perform calibration. ");
		step.setTaskSet(_taskSet[1]);
		stepList.add(step);
		
		step = new CalibrationStep("Step 2", 
				"DO2 Air Calibration",
				"Dip the DO2 sensor in clean water and let it sit in air for " + 
				"at least five (5) minutes. Afterwards, press 'Ok' to perform " +
				"calibration. ");
		step.setTaskSet(_taskSet[2]);
		stepList.add(step);
		
		step = new CalibrationStep("Step 3", 
				"Low-Point Solution Calibration",
				"Immerse the sensor probes in the low-point calibration " +
				"solution (i.e. Solution D) and wait for five (5) minutes to " +
				"let the readings stabilize. Press 'Ok' to proceed with " + 
				"calibration. Default calibration values stored in the config " +
				"file are used for calibration. ");
		step.setTaskSet(_taskSet[3]);
		stepList.add(step);
		
		step = new CalibrationStep("Step 4", 
				"High-Point Solution Calibration",
				"Immerse the sensor probes in the high-point calibration " +
				"solution (i.e. Solution D) and wait for five (5) minutes to " +
				"let the readings stabilize. Press 'Ok' to proceed with " + 
				"calibration. Default calibration values stored in the config " +
				"file are used for calibration. ");
		step.setTaskSet(_taskSet[4]);
		stepList.add(step);
		
		return stepList;
	}
	  
	/*********************/
	/** Private Methods **/
	/*********************/
	

	/***************************/
	/** Private Inner Classes **/
	/***************************/
	private class CalibrationStep {
		private String _name = "";
		private String _desc = "";
		private String _instructions = "";
		private TaskSet _taskSet = null;
		private boolean _completed = false;
		
		public CalibrationStep(String name, 
				String desc, String instructions) {
			this._name = name;
			this._desc = desc;
			this._instructions = instructions;
			return;
		}
		
		public TaskSet getTaskSet() {
			return this._taskSet;
		}
		
		public void setTaskSet(TaskSet taskSet) {
			this._taskSet = taskSet;
			return;
		}
		
		public String getName() {
			return _name;
		}
		
		public void setName(String name) {
			this._name = name;
			return;
		}
		
		public String getDescription() {
			return this._desc;
		}
		
		public void setDescription(String desc) {
			this._desc = desc;
			return;
		}
		
		public String getInstructions() {
			return this._instructions;
		}
		
		public void setInstructions(String instructions) {
			this._instructions = instructions;
			return;
		}
		
		public boolean isCompleted() {
			return this._completed;
		}
		
		public void setCompleted(boolean status) {
			this._completed = status;
			return;
		}
	}

	/*******************************/
	/** Private 'TaskSet' Classes **/
	/*******************************/
	private interface TaskSet {
		public Status executeTasks();
	}
	
	private TaskSet _taskSet[] = { 	new TaskSetStep0(), 
									new TaskSetStep1(),
									new TaskSetStep2(),
									new TaskSetStep3(),
									new TaskSetStep4() };
	
	private class TaskSetStep0 implements TaskSet {
		@Override
		public Status executeTasks() {
			if (_serviceAPI != null) {
				String cmdArr[] = {		"system.main.initSubControllers",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.wait",
										"system.main.destSubControllers" };
				String paramArr[] = { 	"",
										"comm.bluetooth.start",
										"comm.bluetooth.connectByName?HC-05",
										"sensors.water_quality.start",
										"sensors.water_quality.calibratePH?clear",
										"sensors.water_quality.calibrateDO?clear",
										"sensors.water_quality.calibrateEC?clear",
										"2000",
										"" };
				
				try {
					String status = "";
					for (int i = 0; i < cmdArr.length; i++) {
						status = _serviceAPI.runCommand(cmdArr[i], paramArr[i]);
						if (status.equals("FAILED")) {
							OLog.err("Run Task Failed!");
							_serviceAPI.runCommand("system.main.destSubControllers", "");
							return Status.FAILED;
						}
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return Status.OK;
		}
	}

	private class TaskSetStep1 implements TaskSet {
		@Override
		public Status executeTasks() {
			if (_serviceAPI != null) {
				String cmdArr[] = {		"system.main.initSubControllers",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.wait",
										"system.main.destSubControllers" };
				String paramArr[] = { 	"",
										"comm.bluetooth.start",
										"comm.bluetooth.connectByName?HC-05",
										"sensors.water_quality.start",
										"sensors.water_quality.calibrateEC?dry",
										"2000",
										"" };
				
				try {
					String status = "";
					for (int i = 0; i < cmdArr.length; i++) {
						status = _serviceAPI.runCommand(cmdArr[i], paramArr[i]);
						if (status.equals("FAILED")) {
							OLog.err("Run Task Failed!");
							_serviceAPI.runCommand("system.main.destSubControllers", "");
							return Status.FAILED;
						}
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return Status.OK;
		}
	}

	private class TaskSetStep2 implements TaskSet {
		@Override
		public Status executeTasks() {
			if (_serviceAPI != null) {
				String cmdArr[] = {		"system.main.initSubControllers",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.wait",
										"system.main.destSubControllers" };
				String paramArr[] = { 	"",
										"comm.bluetooth.start",
										"comm.bluetooth.connectByName?HC-05",
										"sensors.water_quality.start",
										"sensors.water_quality.calibrateDO?",
										"2000",
										"" };
				
				try {
					String status = "";
					for (int i = 0; i < cmdArr.length; i++) {
						status = _serviceAPI.runCommand(cmdArr[i], paramArr[i]);
						if (status.equals("FAILED")) {
							OLog.err("Run Task Failed!");
							_serviceAPI.runCommand("system.main.destSubControllers", "");
							return Status.FAILED;
						}
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return Status.OK;
		}
	}
	

	private class TaskSetStep3 implements TaskSet {
		@Override
		public Status executeTasks() {
			if (_serviceAPI != null) {
				String cmdArr[] = {		"system.main.initSubControllers",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.wait",
										"system.main.destSubControllers" };
				String paramArr[] = { 	"",
										"comm.bluetooth.start",
										"comm.bluetooth.connectByName?HC-05",
										"sensors.water_quality.start",
										"sensors.water_quality.calibratePH?mid,7.01",
										"sensors.water_quality.calibrateDO?0",
										"sensors.water_quality.calibrateEC?low,27740",
										"2000",
										"" };
				
				try {
					String status = "";
					for (int i = 0; i < cmdArr.length; i++) {
						status = _serviceAPI.runCommand(cmdArr[i], paramArr[i]);
						if (status.equals("FAILED")) {
							OLog.err("Run Task Failed!");
							_serviceAPI.runCommand("system.main.destSubControllers", "");
							return Status.FAILED;
						}
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return Status.OK;
		}
	}

	private class TaskSetStep4 implements TaskSet {
		@Override
		public Status executeTasks() {
			if (_serviceAPI != null) {
				String cmdArr[] = {		"system.main.initSubControllers",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.runTask",
										"system.main.wait",
										"system.main.destSubControllers" };
				String paramArr[] = { 	"",
										"comm.bluetooth.start",
										"comm.bluetooth.connectByName?HC-05",
										"sensors.water_quality.start",
										"sensors.water_quality.calibratePH?high,9.95",
										"sensors.water_quality.calibrateEC?high,104500",
										"2000",
										"" };
				
				try {
					String status = "";
					for (int i = 0; i < cmdArr.length; i++) {
						status = _serviceAPI.runCommand(cmdArr[i], paramArr[i]);
						if (status.equals("FAILED")) {
							OLog.err("Run Task Failed!");
							_serviceAPI.runCommand("system.main.destSubControllers", "");
							return Status.FAILED;
						}
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return Status.OK;
		}
	}
}
