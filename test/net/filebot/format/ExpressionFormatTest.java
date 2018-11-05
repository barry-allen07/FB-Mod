
package net.filebot.format;

import static org.junit.Assert.*;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.junit.Test;

public class ExpressionFormatTest {

	@Test
	public void compile() throws Exception {
		ExpressionFormat format = new TestScriptFormat("");

		Object[] expression = format.compile("name: {name}, number: {number}");

		assertTrue(expression[0] instanceof String);
		assertTrue(expression[1] instanceof CompiledScript);
		assertTrue(expression[2] instanceof String);
		assertTrue(expression[3] instanceof CompiledScript);
	}

	@Test
	public void format() throws Exception {
		assertEquals("X5-452", new TestScriptFormat("X5-{value}").format("452"));

		// padding
		assertEquals("[007]", new TestScriptFormat("[{value.pad(3)}]").format("7"));
		assertEquals("[xx7]", new TestScriptFormat("[{value.pad(3, 'x')}]").format("7"));

		// case
		assertEquals("ALL_CAPS", new TestScriptFormat("{value.upper()}").format("all_caps"));
		assertEquals("lower_case", new TestScriptFormat("{value.lower()}").format("LOWER_CASE"));

		// normalize
		assertEquals("Doctor_Who", new TestScriptFormat("{value.space('_')}").format("Doctor Who"));
		assertEquals("The Day A New Demon Was Born", new TestScriptFormat("{value.upperInitial()}").format("The Day a new Demon was born"));
		assertEquals("Gundam Seed", new TestScriptFormat("{value.lowerTrail()}").format("Gundam SEED"));

		// substring
		assertEquals("first", new TestScriptFormat("{value.before(/[^a-z]/)}").format("first|second"));
		assertEquals("second", new TestScriptFormat("{value.after(/[^a-z]/)}").format("first|second"));

		// replace trailing braces
		assertEquals("The IT Crowd", new TestScriptFormat("{value.replaceTrailingBrackets()}").format("The IT Crowd (UK)"));

		// replace part
		assertEquals("Today Is the Day, Part 1", new TestScriptFormat("{value.replacePart(', Part $1')}").format("Today Is the Day (1)"));
		assertEquals("Today Is the Day, Part 1", new TestScriptFormat("{value.replacePart(', Part $1')}").format("Today Is the Day: part 1"));

		// choice
		assertEquals("not to be", new TestScriptFormat("{value ? 'to be' : 'not to be'}").format(false));
		assertEquals("default", new TestScriptFormat("{value ?: 'default'}").format(false));
	}

	@Test
	public void closures() throws Exception {
		assertEquals("[ant, cat]", new TestScriptFormat("{['ant', 'buffalo', 'cat', 'dinosaur'].findAll{ it.size() <= 3 }}").format(null));
	}

	@Test
	public void illegalSyntax() throws Exception {
		try {
			// will throw exception
			new TestScriptFormat("{value.}");
			// exception must be thrown
			fail("exception expected");
		} catch (ScriptException e) {
			// check message
			assertEquals("SyntaxError: unexpected token: .", e.getMessage());
		}
	}

	@Test
	public void illegalClosingBracket() throws Exception {
		try {
			// will throw exception
			new TestScriptFormat("{{ it -> 'value' }}}");
			// exception must be thrown
			fail("exception expected");
		} catch (ScriptException e) {
			// check message
			assertEquals("SyntaxError: unexpected token: }", e.getMessage());
		}
	}

	@Test(expected = SuppressedThrowables.class)
	public void emptyExpression() throws Exception {
		TestScriptFormat format = new TestScriptFormat("{xyz}");
		format.format(new SimpleBindings());
	}

	@Test
	public void illegalBinding() throws Exception {
		TestScriptFormat format = new TestScriptFormat("Hello {xyz}");
		format.format(new SimpleBindings());

		// check message
		assertEquals("Suppressed: Binding \"xyz\": undefined", format.suppressed().getMessage());
	}

	@Test
	public void illegalProperty() throws Exception {
		TestScriptFormat format = new TestScriptFormat("Hello {value.xyz}");
		format.format("test");

		// check message
		assertEquals("Suppressed: Binding \"xyz\": undefined", format.suppressed().getMessage());
	}

	protected static class TestScriptFormat extends ExpressionFormat {

		public TestScriptFormat(String format) throws ScriptException {
			super(format);
		}

		@Override
		public Bindings getBindings(Object value) {
			Bindings bindings = new SimpleBindings();

			bindings.put("value", value);

			return bindings;
		}

	}

}
