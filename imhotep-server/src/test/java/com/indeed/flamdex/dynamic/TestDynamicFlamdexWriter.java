package com.indeed.flamdex.dynamic;

import com.google.common.collect.ImmutableSet;
import com.indeed.flamdex.api.FlamdexReader;
import com.indeed.flamdex.writer.FlamdexDocument;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author michihiko
 */

public class TestDynamicFlamdexWriter {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final int NUM_DOCS = (2 * 3 * 5 * 7 * 11) + 10;
    private Path directory;

    @Before
    public void setUp() throws IOException {
        directory = temporaryFolder.getRoot().toPath();
    }

    @Test
    public void testSimpleStats() throws IOException {
        final List<FlamdexDocument> documents = new ArrayList<>();
        for (int i = 0; i < NUM_DOCS; ++i) {
            documents.add(DynamicFlamdexTestUtils.makeDocument(i));
        }
        Collections.shuffle(documents);

        final Random random = new Random(0);
        final Path shardDirectory;
        try (final DynamicFlamdexDocWriter flamdexDocWriter = new DynamicFlamdexDocWriter(directory, "shared")) {
            int commitId = 0;
            for (final FlamdexDocument document : documents) {
                if (random.nextInt(200) == 0) {
                    flamdexDocWriter.commit(commitId++, random.nextBoolean());
                }
                flamdexDocWriter.addDocument(document);
            }
            shardDirectory = flamdexDocWriter.commit(commitId++).get();
        }
        try (final FlamdexReader flamdexReader = new DynamicFlamdexReader(shardDirectory)) {
            assertEquals(NUM_DOCS, flamdexReader.getNumDocs());
            assertEquals(NUM_DOCS, flamdexReader.getIntTotalDocFreq("original"));
            int numMod7Mod11i = 0;
            for (int i = 0; i < NUM_DOCS; ++i) {
                numMod7Mod11i += ((i % 7) == (i % 11)) ? 1 : 2;
            }
            assertEquals(numMod7Mod11i, flamdexReader.getIntTotalDocFreq("mod7mod11i"));
            assertEquals(
                    ImmutableSet.of("original", "mod2i", "mod3i", "mod7mod11i", "mod3mod5i_nonzero"),
                    ImmutableSet.copyOf(flamdexReader.getAvailableMetrics()));
            assertEquals(
                    ImmutableSet.of("original", "mod2i", "mod3i", "mod7mod11i", "mod3mod5i_nonzero"),
                    ImmutableSet.copyOf(flamdexReader.getIntFields()));
            assertEquals(
                    ImmutableSet.of("mod5s", "mod7mod11s"),
                    ImmutableSet.copyOf(flamdexReader.getStringFields()));
        }
    }

    @SuppressWarnings("JUnitTestMethodWithNoAssertions")
    @Test
    public void testDeletion() throws IOException {
        final List<FlamdexDocument> documents = new ArrayList<>();
        for (int i = 0; i < NUM_DOCS; ++i) {
            documents.add(DynamicFlamdexTestUtils.makeDocument(i));
        }
        Collections.shuffle(documents);

        final Random random = new Random(0);
        final Set<FlamdexDocument> naive = new HashSet<>();
        final Path shardDirectory;
        try (final DynamicFlamdexDocWriter flamdexDocWriter = new DynamicFlamdexDocWriter(directory, "shard")) {
            int commitId = 0;
            for (final FlamdexDocument document : documents) {
                if (random.nextInt(200) == 0) {
                    flamdexDocWriter.commit(commitId++, random.nextBoolean());
                }
                if (random.nextInt(10) == 0) {
                    DynamicFlamdexTestUtils.removeDocument(naive, flamdexDocWriter, "original", random.nextInt(NUM_DOCS));
                }
                DynamicFlamdexTestUtils.addDocument(naive, flamdexDocWriter, document);
            }
            shardDirectory = flamdexDocWriter.commit(commitId++).get();
        }
        final FlamdexReader reader = new DynamicFlamdexReader(shardDirectory);
        DynamicFlamdexTestUtils.validateIndex(naive, reader);
    }

    @SuppressWarnings("JUnitTestMethodWithNoAssertions")
    @Test
    public void testMultipleDeletion() throws IOException {
        final List<FlamdexDocument> documents = new ArrayList<>();
        for (int i = 0; i < NUM_DOCS; ++i) {
            documents.add(DynamicFlamdexTestUtils.makeDocument(i));
        }
        Collections.shuffle(documents);

        final Random random = new Random(0);
        final Set<FlamdexDocument> naive = new HashSet<>();
        final Path shardDirectory;
        try (final DynamicFlamdexDocWriter flamdexDocWriter = new DynamicFlamdexDocWriter(directory, "shard")) {
            int cnt = 0;
            for (final FlamdexDocument document : documents) {
                if (random.nextInt(200) == 0) {
                    flamdexDocWriter.commit(cnt, random.nextBoolean());
                }
                if (((++cnt) % 500) == 0) {
                    DynamicFlamdexTestUtils.removeDocument(naive, flamdexDocWriter, "mod7mod11i", random.nextInt(7));
                }
                DynamicFlamdexTestUtils.addDocument(naive, flamdexDocWriter, document);
            }
            shardDirectory = flamdexDocWriter.commit(cnt++).get();
        }
        final FlamdexReader reader = new DynamicFlamdexReader(shardDirectory);
        DynamicFlamdexTestUtils.validateIndex(naive, reader);
    }

    @SuppressWarnings("JUnitTestMethodWithNoAssertions")
    @Test
    public void testWithDeletionAndMerge() throws IOException, InterruptedException {
        final ExecutorService executorService = Executors.newFixedThreadPool(4);
        final List<FlamdexDocument> documents = new ArrayList<>();
        for (int i = 0; i < NUM_DOCS; ++i) {
            documents.add(DynamicFlamdexTestUtils.makeDocument(i));
        }
        Collections.shuffle(documents);

        final Random random = new Random(0);
        final Set<FlamdexDocument> naive = new HashSet<>();
        final Path shardDirectory;
        try (final DynamicFlamdexDocWriter flamdexDocWriter = new DynamicFlamdexDocWriter(directory, "shard", new MergeStrategy.ExponentialMergeStrategy(4, 5), executorService)) {
            int cnt = 0;
            for (final FlamdexDocument document : documents) {
                if (random.nextInt(50) == 0) {
                    flamdexDocWriter.commit(cnt, random.nextBoolean());
                }
                if (((++cnt) % 500) == 0) {
                    DynamicFlamdexTestUtils.removeDocument(naive, flamdexDocWriter, "mod7mod11i", random.nextInt(7));
                }
                DynamicFlamdexTestUtils.addDocument(naive, flamdexDocWriter, document);
            }
            shardDirectory = flamdexDocWriter.commit(cnt).get();
        }
        final FlamdexReader reader = new DynamicFlamdexReader(shardDirectory);
        DynamicFlamdexTestUtils.validateIndex(naive, reader);
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);
    }
}
