package net.filebot.util;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class XPathUtilities {

	public static Node selectNode(String xpath, Object node) {
		return (Node) evaluateXPath(xpath, node, XPathConstants.NODE);
	}

	public static String selectString(String xpath, Object node) {
		return ((String) evaluateXPath(xpath, node, XPathConstants.STRING)).trim();
	}

	public static Stream<Node> streamNodes(String xpath, Object node) {
		return stream((NodeList) evaluateXPath(xpath, node, XPathConstants.NODESET));
	}

	public static Node[] selectNodes(String xpath, Object node) {
		return streamNodes(xpath, node).toArray(Node[]::new);
	}

	public static List<String> selectStrings(String xpath, Object node) {
		List<String> values = new ArrayList<String>();
		for (Node it : selectNodes(xpath, node)) {
			String textContent = getTextContent(it);
			if (textContent.length() > 0) {
				values.add(textContent);
			}
		}
		return values;
	}

	/**
	 * @param nodeName
	 *            search for nodes with this name
	 * @param parentNode
	 *            search in the child nodes of this nodes
	 * @return text content of the child node or null if no child with the given name was found
	 */
	public static Node getChild(String nodeName, Node parentNode) {
		if (parentNode == null) {
			return null;
		} else {
			return stream(parentNode.getChildNodes()).filter(n -> nodeName.equals(n.getNodeName())).findFirst().orElse(null);
		}
	}

	public static Node[] getChildren(String nodeName, Node parentNode) {
		if (parentNode == null) {
			return new Node[0];
		} else {
			return stream(parentNode.getChildNodes()).filter(n -> nodeName.equals(n.getNodeName())).toArray(Node[]::new);
		}
	}

	public static String getAttribute(String attribute, Node node) {
		if (node != null) {
			Node attr = node.getAttributes().getNamedItem(attribute);
			if (attr != null) {
				return attr.getNodeValue().trim();
			}
		}
		return null;
	}

	/**
	 * Get text content of the first child node matching the given node name. Use this method instead of {@link #selectString(String, Object)} whenever xpath support is not required, because it is much faster, especially for large documents.
	 *
	 * @param childName
	 *            search for nodes with this name
	 * @param parentNode
	 *            search in the child nodes of this nodes
	 * @return text content of the child node or null if no child with the given name was found
	 */
	public static String getTextContent(String childName, Node parentNode) {
		Node child = getChild(childName, parentNode);

		if (child == null) {
			return null;
		}

		return getTextContent(child);
	}

	public static String getTextContent(Node node) {
		StringBuilder sb = new StringBuilder();

		for (Node textNode : getChildren("#text", node)) {
			sb.append(textNode.getNodeValue());
		}

		return sb.toString().trim();
	}

	public static List<String> getListContent(String childName, String delimiter, Node parentNode) {
		List<String> list = new ArrayList<String>();
		for (Node node : getChildren(childName, parentNode)) {
			String textContent = getTextContent(node);
			if (textContent != null && textContent.length() > 0) {
				if (delimiter == null) {
					list.add(textContent);
				} else {
					for (String it : textContent.split(delimiter)) {
						it = it.trim();
						if (it.length() > 0) {
							list.add(it);
						}
					}
				}
			}
		}
		return list;
	}

	public static Double getDecimal(String textContent) {
		try {
			return Double.parseDouble(textContent);
		} catch (NumberFormatException | NullPointerException e) {
			return null;
		}
	}

	public static Object evaluateXPath(String xpath, Object item, QName returnType) {
		try {
			return XPathFactory.newInstance().newXPath().compile(xpath).evaluate(item, returnType);
		} catch (XPathExpressionException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static Stream<Node> streamElements(Node parent) {
		return stream(parent.getChildNodes()).filter(n -> n.getNodeType() == Node.ELEMENT_NODE);
	}

	public static Stream<Node> stream(NodeList nodes) {
		return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item);
	}

	public static <K extends Enum<K>> EnumMap<K, String> getEnumMap(Node node, Class<K> cls) {
		EnumMap<K, String> map = new EnumMap<K, String>(cls);
		for (K key : cls.getEnumConstants()) {
			String value = getTextContent(key.name(), node);
			if (value != null && value.length() > 0) {
				map.put(key, value);
			}
		}
		return map;
	}

	private XPathUtilities() {
		throw new UnsupportedOperationException();
	}

}
