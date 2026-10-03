package com.app.fimtale.model;
/** Display taxonomy used by the work detail and share card. */
public class TopicTags extends Tags {
    public static TopicTags from(Tags tags) {
        TopicTags result = new TopicTags();
        result.setType(tags.getType()); result.setSource(tags.getSource());
        result.setRating(tags.getRating()); result.setLength(tags.getLength());
        result.setStatus(tags.getStatus()); result.setOtherTags(tags.getOtherTags());
        return result;
    }
}
