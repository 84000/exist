/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xpath;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.dom.ContextItem;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.Type;

/**
 * Abstract superclass for variable binding expressions like "for" and "let".
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public abstract class BindingExpression extends AbstractExpression {

	protected final static Logger LOG =
		Logger.getLogger(BindingExpression.class);

	protected String varName;
	protected Expression inputSequence;
	protected Expression returnExpr;
	protected Expression whereExpr;

	public BindingExpression(StaticContext context) {
		super(context);
	}

	public void setVariable(String qname) {
		varName = qname;
	}

	public void setInputSequence(Expression sequence) {
		inputSequence = sequence;
	}

	public void setReturnExpression(Expression expr) {
		returnExpr = expr;
	}

	public void setWhereExpression(Expression expr) {
		whereExpr = expr;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public abstract Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException;

	protected Sequence applyWhereExpression(
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence)
		throws XPathException {
		whereExpr.setInPredicate(true);
		if (Type.subTypeOf(whereExpr.returnsType(), Type.NODE)) {
			// if the where expression returns a node set, check the context
			// node of each node in the set
			LOG.debug("calling single: " + whereExpr);
			NodeSet temp = whereExpr.eval(docs, contextSequence).toNodeSet();
			NodeProxy current;
			ContextItem contextNode;
			NodeProxy next;
			DocumentImpl lastDoc = null;
			int count = 0, sizeHint = -1;
			NodeSet result = new ExtArrayNodeSet();
			for (Iterator i = temp.iterator(); i.hasNext(); count++) {
				current = (NodeProxy) i.next();
				if (lastDoc == null || current.doc != lastDoc) {
					lastDoc = current.doc;
					sizeHint = temp.getSizeHint(lastDoc);
				}
				contextNode = current.getContext();
				if (contextNode == null) {
					throw new XPathException("Internal evaluation error: context node is missing!");
				}
				while (contextNode != null) {
					next = contextNode.getNode();
					next.addMatches(current.match);
					if (!result.contains(next))
						result.add(next, sizeHint);
					contextNode = contextNode.getNextItem();
				}
			}
			return result;
		} else {
			// general where clause: just check the effective boolean value
			NodeSet result = new ExtArrayNodeSet();
			int p = 0;
			context.setContextPosition(0);
			for (SequenceIterator i = contextSequence.iterate();
				i.hasNext();
				p++) {
				Item item = i.nextItem();
				context.setContextPosition(p);
				Sequence innerSeq = whereExpr.eval(docs, contextSequence, item);
				if (innerSeq.effectiveBooleanValue())
					result.add(item);
			}
			return result;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#preselect(org.exist.dom.DocumentSet, org.exist.xpath.StaticContext)
	 */
	public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
		return in_docs;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public abstract String pprint();

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.ITEM;
	}

}
