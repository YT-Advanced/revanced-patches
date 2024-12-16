package app.revanced.extension.youtube.patches.components;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import app.revanced.extension.shared.patches.components.ByteArrayFilterGroup;
import app.revanced.extension.shared.patches.components.ByteArrayFilterGroupList;
import app.revanced.extension.shared.patches.components.Filter;
import app.revanced.extension.shared.patches.components.FilterGroup;
import app.revanced.extension.shared.patches.components.StringFilterGroup;
import app.revanced.extension.shared.utils.Logger;
import app.revanced.extension.shared.utils.TrieSearch;
import app.revanced.extension.youtube.patches.utils.ReturnYouTubeDislikePatch;
import app.revanced.extension.youtube.settings.Settings;
import app.revanced.extension.youtube.shared.VideoInformation;

/**
 * @noinspection ALL
 * <p>
 * Searches for video id's in the proto buffer of Shorts dislike.
 * <p>
 * Because multiple litho dislike spans are created in the background
 * (and also anytime litho refreshes the components, which is somewhat arbitrary),
 * that makes the value of {@link VideoInformation#getVideoId()} and {@link VideoInformation#getPlayerResponseVideoId()}
 * unreliable to determine which video id a Shorts litho span belongs to.
 * <p>
 * But the correct video id does appear in the protobuffer just before a Shorts litho span is created.
 * <p>
 * Once a way to asynchronously update litho text is found, this strategy will no longer be needed.
 */
public final class ReturnYouTubeDislikeFilterPatch extends Filter {

    /**
     * Last unique video id's loaded.
     * Key is a String represeting the video id.
     * Value is a ByteArrayFilterGroup used for performing KMP pattern searching.
     */
    @GuardedBy("itself")
    private static final Map<String, ByteArrayFilterGroup> lastVideoIds = new LinkedHashMap<>() {
        /**
         * Number of video id's to keep track of for searching thru the buffer.
         * A minimum value of 3 should be sufficient, but check a few more just in case.
         */
        private static final int NUMBER_OF_LAST_VIDEO_IDS_TO_TRACK = 5;

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > NUMBER_OF_LAST_VIDEO_IDS_TO_TRACK;
        }
    };
    private final ByteArrayFilterGroupList videoIdFilterGroup = new ByteArrayFilterGroupList();

    public ReturnYouTubeDislikeFilterPatch() {
        // When a new Short is opened, the like buttons always seem to load before the dislike.
        // But if swiping back to a previous video and liking/disliking, then only that single button reloads.
        // So must check for both buttons.
        addPathCallbacks(
                new StringFilterGroup(null, "|shorts_like_button.eml"),
                new StringFilterGroup(null, "|shorts_dislike_button.eml")
        );

        // After the button identifiers is binary data and then the video id for that specific short.
        videoIdFilterGroup.addAll(
                new ByteArrayFilterGroup(null, "id.reel_like_button"),
                new ByteArrayFilterGroup(null, "id.reel_dislike_button")
        );
    }

    private volatile static String shortsVideoId = "";

    public static String getShortsVideoId() {
        return shortsVideoId;
    }

    /**
     * Injection point.
     */
    public static void newShortsVideoStarted(@NonNull String newlyLoadedChannelId, @NonNull String newlyLoadedChannelName,
                                             @NonNull String newlyLoadedVideoId, @NonNull String newlyLoadedVideoTitle,
                                             final long newlyLoadedVideoLength, boolean newlyLoadedLiveStreamValue) {
        if (!Settings.RYD_SHORTS.get()) {
            return;
        }
        if (shortsVideoId.equals(newlyLoadedVideoId)) {
            return;
        }
        Logger.printDebug(() -> "newShortsVideoStarted: " + newlyLoadedVideoId);
        shortsVideoId = newlyLoadedVideoId;
    }

    /**
     * Injection point.
     */
    public static void newPlayerResponseVideoId(String videoId, boolean isShortAndOpeningOrPlaying) {
        try {
            if (!isShortAndOpeningOrPlaying || !Settings.RYD_ENABLED.get() || !Settings.RYD_SHORTS.get()) {
                return;
            }
            synchronized (lastVideoIds) {
                if (lastVideoIds.containsKey(videoId)) return;
                Logger.printDebug(() -> "New Shorts video id: " + videoId);

                final ByteArrayFilterGroup videoIdFilter = new ByteArrayFilterGroup(null, videoId);
                lastVideoIds.put(videoId, videoIdFilter);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "newPlayerResponseVideoId failure", ex);
        }
    }

    @Override
    public boolean isFiltered(String path, @Nullable String identifier, String allValue, byte[] protobufBufferArray,
                              StringFilterGroup matchedGroup, FilterContentType contentType, int contentIndex) {
        if (!Settings.RYD_ENABLED.get() || !Settings.RYD_SHORTS.get()) {
            return false;
        }

        FilterGroup.FilterGroupResult result = videoIdFilterGroup.check(protobufBufferArray);
        if (result.isFiltered()) {
            String matchedVideoId = findVideoId(protobufBufferArray);
            // Matched video will be null if in incognito mode.
            // Must pass a null id to correctly clear out the current video data.
            // Otherwise if a Short is opened in non-incognito, then incognito is enabled and another Short is opened,
            // the new incognito Short will show the old prior data.
            ReturnYouTubeDislikePatch.setLastLithoShortsVideoId(matchedVideoId);
        }

        return false;
    }

    @Nullable
    private String findVideoId(byte[] protobufBufferArray) {
        synchronized (lastVideoIds) {
            for (Map.Entry<String, ByteArrayFilterGroup> entry : lastVideoIds.entrySet()) {
                final ByteArrayFilterGroup videoIdFilter = entry.getValue();

                if (videoIdFilter.check(protobufBufferArray).isFiltered()) {
                    return entry.getKey(); // Return videoId
                }
            }

            return null;
        }
    }
}
