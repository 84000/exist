/*
 * Created on Sep 12, 2003
 *
 */
package org.exist.examples.triggers;

import java.io.StringReader;
import java.util.Map;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.triggers.FilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.storage.DBBroker;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * This trigger maintains a file "contents.xml", containing a list of all documents added to a collection.
 * It uses XUpdate to update "contents.xml" whenever a document is added or removed.
 * 
 * @author wolf
 */
public class ExampleTrigger extends FilteringTrigger {

	private DocumentImpl doc;
	
	/* (non-Javadoc)
	 * @see org.exist.collections.Trigger#prepare(java.lang.String, org.w3c.dom.Document)
	 */
	public void prepare(int event, DBBroker broker, String documentName, Document existingDocument)
		throws TriggerException {
		String xupdate;
		// we react to the store and remove events
		if(event == STORE_DOCUMENT_EVENT)
			// create XUpdate command for inserts
			xupdate = "<?xml version=\"1.0\"?>" +
				"<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
				"<xu:append select='/contents'><xu:element name='file'>" +
				documentName +
				"</xu:element></xu:append></xu:modifications>";
		else if(event == REMOVE_DOCUMENT_EVENT)
			// create XUpdate command for removals
			xupdate = "<?xml version=\"1.0\"?>" +
				"<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
				"<xu:remove select=\"//file[text()='" + documentName + "']\"></xu:remove>" +
				"</xu:modifications>";
		else
			return;
		getLogger().debug(xupdate);
		// create a document set containing "contents.xml"
		DocumentSet docs = new DocumentSet();
		docs.add(this.doc);
		try {
			// IMPORTANT: temporarily disable triggers on the collection.
			// We would end up in infinite recursion if we don't do that
			getCollection().setTriggersEnabled(false);
			// create the XUpdate processor
			XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
			// process the XUpdate
			Modification modifications[] = processor.parse(new InputSource(new StringReader(xupdate)));
			for(int i = 0; i < modifications.length; i++)
				modifications[i].process();
			broker.flush();
		} catch (Exception e) {
			throw new TriggerException(e.getMessage(), e);
		} finally {
			// IMPORTANT: reenable trigger processing for the collection.
			getCollection().setTriggersEnabled(true);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.Trigger#configure(org.exist.storage.DBBroker, org.exist.collections.Collection, java.util.Map)
	 */
	public void configure(DBBroker broker, Collection parent, Map parameters)
		throws CollectionConfigurationException {
		super.configure(broker, parent, parameters);
		// IMPORTANT: temporarily disable triggers on the collection.
		// We would end up in infinite recursion if we don't do that
		parent.setTriggersEnabled(false);
		// the name of the contents file can be set through parameters
		String contentsFile = (String)parameters.get("contents");
		if(contentsFile == null)
			contentsFile = "contents.xml";
		// try to retrieve the contents file
		this.doc = parent.getDocument(parent.getName() + '/' + contentsFile);
		if(this.doc == null)
			// doesn't exist yet: create it
			try {
				getLogger().debug("creating new file for collection contents");
				this.doc = parent.addDocument(broker, contentsFile, "<?xml version=\"1.0\"?><contents></contents>");
			} catch (Exception e) {
				throw new CollectionConfigurationException(e.getMessage(), e);
			} finally {
				parent.setTriggersEnabled(true);
			}
	}

}
