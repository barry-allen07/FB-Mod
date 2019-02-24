package net.filebot;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import net.filebot.format.ExpressionFormatTest;
import net.filebot.hash.VerificationFormatTest;
import net.filebot.media.MediaDetectionTest;
import net.filebot.media.ReleaseInfoTest;
import net.filebot.media.VideoFormatTest;
import net.filebot.mediainfo.MediaInfoTest;
import net.filebot.similarity.EpisodeMetricsTest;
import net.filebot.similarity.SimilarityTestSuite;
import net.filebot.subtitle.SubtitleReaderTestSuite;
import net.filebot.ui.rename.MatchModelTest;
import net.filebot.util.UtilTestSuite;
import net.filebot.web.WebTestSuite;

@RunWith(Suite.class)
@SuiteClasses({ ExpressionFormatTest.class, VerificationFormatTest.class, MatchModelTest.class, EpisodeMetricsTest.class, ReleaseInfoTest.class, VideoFormatTest.class, MediaDetectionTest.class, MediaInfoTest.class, SimilarityTestSuite.class, WebTestSuite.class, SubtitleReaderTestSuite.class, UtilTestSuite.class })
public class AllTests {

}
