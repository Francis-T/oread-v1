package net.oukranos.oreadv1.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import net.oukranos.oreadv1.types.CalibrationData;

public class CalibConfigXmlParser {
	private List<CalibrationData> _calibDataList = null;
	
	public CalibConfigXmlParser() { return; }
	
	public List<CalibrationData> parseXml(String filename) throws XmlPullParserException, IOException {
		XmlPullParser xpp = null;
		File f = new File(filename);
		
		if (f.exists() == false) {
			return null;
		}
		
		_calibDataList = new ArrayList<CalibrationData>();
		
		xpp = getParser(); 
		if (xpp == null) {
			return null;
		}
		
		try {
			xpp.setInput(new FileReader(f));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		int eventType = xpp.getEventType();
		do {
			switch(eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;
				case XmlPullParser.END_DOCUMENT:
					break;
				case XmlPullParser.START_TAG:
					processStartTag(xpp);
					break;
				case XmlPullParser.END_TAG:
					processEndTag(xpp);
					break;
				case XmlPullParser.TEXT:
					break;
				default:
					break;
			}
			eventType = xpp.next();
		} while (eventType != XmlPullParser.END_DOCUMENT);
		
		return this._calibDataList;
	}

	/*********************/
	/** Private Methods **/
	/*********************/
	private XmlPullParser getParser() {
		XmlPullParser xpp = null;
		XmlPullParserFactory factory;
		try {
			factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(true);
			xpp = factory.newPullParser();
		} catch (XmlPullParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return xpp;
	}
	
	/************************************/
	/** Private Tag Processing Methods **/
	/************************************/
	private void processStartTag(XmlPullParser xpp) {
		if (xpp == null) {
			return;
		}
		
		/* Get the tag info */
		String tagName = xpp.getName();
		
		/* Process the tag */
		if (tagName.equals("calib-data") == true) {
			processCalibDataTag(xpp);
		}
		
		return;
	}

	private void processCalibDataTag(XmlPullParser xpp) {
		String calibTitle = "";
		String calibSensor = "";
		String calibUnits = "";
		String calibMode = "";
		String calibCommand = "";
		String calibPrefix = "";
		String calibInstruct = "";
		boolean calibAllowRead = false;
		boolean calibAllowParams = false;
		
		for (int attrIdx = 0; attrIdx < xpp.getAttributeCount(); attrIdx++) {
			String attrName = xpp.getAttributeName(attrIdx); 
            String attrValue = xpp.getAttributeValue(attrIdx);

			if (attrName.equals("title") == true) {
				calibTitle = attrValue;
			} else if (attrName.equals("sensor") == true) {
				calibSensor = attrValue;
			} else if (attrName.equals("units") == true) {
				calibUnits = attrValue;
			} else if (attrName.equals("mode") == true) {
				calibMode = attrValue;
			} else if (attrName.equals("command") == true) {
				calibCommand = attrValue;
			} else if (attrName.equals("prefix") == true) {
				calibPrefix = attrValue;
			} else if (attrName.equals("instructions") == true) {
				calibInstruct = attrValue;
			} else if (attrName.equals("allowRead") == true) {
				if (attrValue.equals("true")) {
					calibAllowRead = true;
				} else if (attrValue.equals("false")) {
					calibAllowRead = false;
				}
			} else if (attrName.equals("allowParams") == true) {
				if (attrValue.equals("true")) {
					calibAllowParams = true;
				} else if (attrValue.equals("false")) {
					calibAllowParams = false;
				}
			}
		}
		
		/* Construct the calibration data object */
		CalibrationData cd = new CalibrationData(calibSensor, calibMode, calibCommand);
		cd.setTitle(calibTitle);
		cd.setUnits(calibUnits);
		cd.setParamPrefix(calibPrefix);
		cd.setInstructions(calibInstruct);
		cd.setAllowParams(calibAllowParams);
		cd.setAllowRead(calibAllowRead);
		
		/* Add it to the list */
		if (_calibDataList != null) {
			_calibDataList.add(cd);	
		}
		
		return;
	}

	
	private void processEndTag(XmlPullParser xpp) {
		return;
		
	}

	/*********************************/
	/** Main Function (for testing) **/
	/*********************************/
	public static void main(String args[]) {
		CalibConfigXmlParser cfparse = new CalibConfigXmlParser();
		try {
			cfparse.parseXml("calib-config.xml");
		} catch (Exception e) {
			e.printStackTrace();
		}
		//System.out.print(config.toString());
		
		return;
	}
}
