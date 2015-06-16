package net.oukranos.oreadv1.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Stack;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import net.oukranos.oreadv1.types.Configuration;
import net.oukranos.oreadv1.types.Data;
import net.oukranos.oreadv1.types.Procedure;
import net.oukranos.oreadv1.types.Status;

public class ConfigXmlParser {
	
	public ConfigXmlParser() { return; }
	
	public Status parseXml(String filename, Configuration config) throws XmlPullParserException, IOException {
		XmlPullParser xpp = null;
		File f = new File(filename);
		
		if (f.exists() == false) {
			return Status.FAILED;
		}
		
		xpp = getParser(); 
		if (xpp == null) {
			return Status.FAILED;
		}
		
		try {
			xpp.setInput(new FileReader(f));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		XmlParsingMetaData xmpData = new XmlParsingMetaData();
		int eventType = xpp.getEventType();
		do {
			switch(eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;
				case XmlPullParser.END_DOCUMENT:
					break;
				case XmlPullParser.START_TAG:
					processStartTag(xpp, xmpData, config);
					break;
				case XmlPullParser.END_TAG:
					processEndTag(xpp, xmpData);
					break;
				case XmlPullParser.TEXT:
					break;
				default:
					break;
			}
			eventType = xpp.next();
		} while (eventType != XmlPullParser.END_DOCUMENT);
		
		return Status.OK;
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
	private void processStartTag(XmlPullParser xpp, XmlParsingMetaData xmpData, 
			Configuration config) {
		if ((xpp == null) || (xmpData == null)) {
			return;
		}
		
		/* Get the tag info */
		String tagName = xpp.getName();
		String tagId = "";
		int depth = xpp.getDepth();
		for (int attrIdx = 0; attrIdx < xpp.getAttributeCount(); attrIdx++) {
			if (xpp.getAttributeName(attrIdx).equals("id") == true) {
				tagId = xpp.getAttributeValue(attrIdx);
			}
		}
		
		/* Store info about this tag on the meta data stack */
		xmpData.addElement(tagName, tagId, depth);
		
		if (tagName.equals("module") == true) {
			processModuleTag(xpp, xmpData, config);
		} else if (tagName.equals("procedure") == true) {
			processProcedureTag(xpp, xmpData, config);
		} else if (tagName.equals("data") == true) {
			processDataTag(xpp, xmpData, config);
		} else if (tagName.equals("task") == true) {
			processTaskTag(xpp, xmpData, config);
		} else if (tagName.equals("configuration") == true) {
			processConfigTag(xpp, xmpData, config);
		}
		
		return;
	}

	private void processConfigTag(XmlPullParser xpp, XmlParsingMetaData xmpData, 
			Configuration config) {
		String configId = "";
		String configVersion = "";
		String configDate = "";
		
		for (int attrIdx = 0; attrIdx < xpp.getAttributeCount(); attrIdx++) {
			String attrName = xpp.getAttributeName(attrIdx); 
            String attrValue = xpp.getAttributeValue(attrIdx);

			if (attrName.equals("id") == true) {
				configId = attrValue;
			} else if (attrName.equals("version") == true) {
				configVersion = attrValue;
			} else if (attrName.equals("creation-date") == true) {
				configDate = attrValue;
			}
		}
		
		/* Set the config file parameters */
		config.setId(configId);
		config.setVersion(configVersion);
		config.setCreationDate(configDate);
		
		return;
	}
	
	private void processModuleTag(XmlPullParser xpp, XmlParsingMetaData xmpData, 
			Configuration config) {
		String moduleId = "";
		String moduleType = "";
		
		for (int attrIdx = 0; attrIdx < xpp.getAttributeCount(); attrIdx++) {
			String attrName = xpp.getAttributeName(attrIdx); 
            String attrValue = xpp.getAttributeValue(attrIdx);

			if (attrName.equals("id") == true) {
				moduleId = attrValue;
			} else if (attrName.equals("type") == true) {
				moduleType = attrValue;
			}
		}
		
		config.addModule(moduleId, moduleType);
		return;
	}

	private void processProcedureTag(XmlPullParser xpp, XmlParsingMetaData xmpData, 
			Configuration config) {
		String procedureId = "";
		
		for (int attrIdx = 0; attrIdx < xpp.getAttributeCount(); attrIdx++) {
			String attrName = xpp.getAttributeName(attrIdx); 
            String attrValue = xpp.getAttributeValue(attrIdx);

			if (attrName.equals("id") == true) {
				procedureId = attrValue;
			}
		}
		
		config.addProcedure(procedureId);
		return;
	}

	private void processDataTag(XmlPullParser xpp, XmlParsingMetaData xmpData, 
			Configuration config) {
		String dataId = "";
		String dataType = "";
		String dataValue = "";
		
		for (int attrIdx = 0; attrIdx < xpp.getAttributeCount(); attrIdx++) {
			String attrName = xpp.getAttributeName(attrIdx); 
            String attrValue = xpp.getAttributeValue(attrIdx);

			if (attrName.equals("id") == true) {
				dataId = attrValue;
			} else if (attrName.equals("type") == true) {
				dataType = attrValue;
			} else if (attrName.equals("value") == true) {
				dataValue = attrValue;
			}
		}
		
		ConfigXmlElement parentElem = xmpData.getLastElementParent();
		if (parentElem.getTag().equals("data")) {
			Data parentData = config.getData(parentElem.getId());
			if (parentData != null) {
				parentData.addData(dataId, dataType, dataValue);
			}
		} else {
			config.addData(dataId, dataType, dataValue);
		}
		
		return;
	}

	private void processTaskTag(XmlPullParser xpp, XmlParsingMetaData xmpData, 
			Configuration config) {
		String taskId = "";
		String taskParams = "";
		
		for (int attrIdx = 0; attrIdx < xpp.getAttributeCount(); attrIdx++) {
			String attrName = xpp.getAttributeName(attrIdx); 
            String attrValue = xpp.getAttributeValue(attrIdx);

			if (attrName.equals("id") == true) {
				taskId = attrValue;
			} else if (attrName.equals("params") == true) {
				taskParams = attrValue;
			}
		}
		
		ConfigXmlElement parentElem = xmpData.getLastElementParent();
		if (parentElem.getTag().equals("procedure")) {
			Procedure parentProcedure = config.getProcedure(parentElem.getId());
			if (parentProcedure != null) {
				parentProcedure.addTask(taskId, taskParams);
			}
		}
		
		return;
	}
	
	private void processEndTag(XmlPullParser xpp, XmlParsingMetaData xmpData) {
		if ((xpp == null) || (xmpData == null)) {
			return;
		}
		
		/* Get the tag info */
		String tagName = xpp.getName();
		int depth = xpp.getDepth();
		
		ConfigXmlElement lastElement = xmpData.getLastElement();
		if ((lastElement.getTag().equals(tagName) == false) && 
				(lastElement.getDepth() != depth)) {
			return;
		}
		
		xmpData.removeLastElement();
		
		return;
		
	}	

	/***************************/
	/** Private Inner Classes **/
	/***************************/
	private class ConfigXmlElement {
		private String _tag = "";
		private String _id = "";
		private int _depth = -1;
		
		public ConfigXmlElement(String tag, String id, int depth) {
			this._tag = tag;
			this._id = id;
			this._depth = depth;
			return;
		}
		
		public String getTag() {
			return (this._tag);
		}
		
		public String getId() {
			return (this._id);
		}
		
		public int getDepth() {
			return (this._depth);
		}
		
		public String toString() {
			return ("tag=" + _tag + " " +
					"id=" + _id + " " +
					"depth=" + _depth);
		}
	}
	
	private class XmlParsingMetaData {
		private Stack<ConfigXmlElement> _tagStack = null;
		
		public XmlParsingMetaData() {
			this._tagStack = new Stack<ConfigXmlElement>();
			return;
		}
		
		public Status addElement(String tag, String id, int depth) {
			if ((tag == null) || (id == null)) {
				return Status.FAILED;
			}
			
			if ((tag.isEmpty() == true) || (tag.isEmpty() == true) ||
					(depth < 0)) {
				return Status.FAILED;
			}
			
			if (this._tagStack == null) {
				return Status.FAILED;
			}
			
			this._tagStack.push(new ConfigXmlElement(tag, id, depth));
			
			return Status.OK;
		}
		
		public ConfigXmlElement removeLastElement() {
			if (this._tagStack == null) {
				return null;
			}
			
			if (this._tagStack.isEmpty() == true) {
				return null;
			}
			
			return (this._tagStack.pop());
		}

		public ConfigXmlElement getLastElement() {
			if (this._tagStack == null) {
				return null;
			}
			
			if (this._tagStack.isEmpty() == true) {
				return null;
			}
			
			return (this._tagStack.peek());
		}

		public ConfigXmlElement getLastElementParent() {
			if (this._tagStack == null) {
				return null;
			}
			
			if (this._tagStack.isEmpty() == true) {
				return null;
			}
			
			return (this._tagStack.get(this._tagStack.size()-2));
		}
	}
	

	/*********************************/
	/** Main Function (for testing) **/
	/*********************************/
	public static void main(String args[]) {
		Configuration config = new Configuration("Oread");
		ConfigXmlParser cfparse = new ConfigXmlParser();
		try {
			cfparse.parseXml("test.xml", config);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.print(config.toString());
		
		return;
	}
}
