package net.filebot.util;

import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.Collectors.*;
import static net.filebot.util.RegularExpressions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;

public class PGP {

	public static String verifyClearSignMessage(String psm, InputStream pub) throws Exception {
		ArmoredInputStream armoredInput = new ArmoredInputStream(new ByteArrayInputStream(psm.getBytes(UTF_8)));

		// read content
		ByteBufferOutputStream content = new ByteBufferOutputStream(256);
		int character;

		while ((character = armoredInput.read()) >= 0 && armoredInput.isClearText()) {
			content.write(character);
		}

		// read public key
		PGPPublicKeyRing publicKeyRing = new PGPPublicKeyRing(pub, new BcKeyFingerprintCalculator());
		PGPPublicKey publicKey = publicKeyRing.getPublicKey();

		// read signature
		PGPSignatureList signatureList = (PGPSignatureList) new BcPGPObjectFactory(armoredInput).nextObject();
		PGPSignature signature = signatureList.get(0);
		signature.init(new BcPGPContentVerifierBuilderProvider(), publicKey);

		// normalize clear sign message
		String clearSignMessage = NEWLINE.splitAsStream(UTF_8.decode(content.getByteBuffer())).map(String::trim).collect(joining("\r\n"));

		// verify signature
		signature.update(clearSignMessage.getBytes(UTF_8));

		if (!signature.verify()) {
			throw new PGPException("Bad Signature");
		}

		return clearSignMessage;
	}

	private static final Pattern PGP_SIGNED_MESSAGE = Pattern.compile("-----BEGIN PGP SIGNED MESSAGE-----(.*?)-----END PGP SIGNATURE-----", Pattern.MULTILINE | Pattern.DOTALL);

	public static String findClearSignMessage(CharSequence content) {
		Matcher matcher = PGP_SIGNED_MESSAGE.matcher(content);
		if (matcher.find()) {
			return matcher.group();
		}

		throw new IllegalArgumentException("PGP SIGNED MESSAGE not found");
	}

}
