CREATE TABLE IF NOT EXISTS lesson_discussion_comments (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP,
    updated_by UUID,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    course_id UUID NOT NULL,
    course_lesson_id UUID NOT NULL,
    user_id UUID NOT NULL,
    parent_id UUID NULL,
    depth INTEGER NOT NULL DEFAULT 0,
    content TEXT NOT NULL,
    likes_count INTEGER NOT NULL DEFAULT 0,
    reply_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_lesson_discussion_comments_course
        FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_lesson_discussion_comments_course_lesson
        FOREIGN KEY (course_lesson_id) REFERENCES course_lessons(id),
    CONSTRAINT fk_lesson_discussion_comments_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_lesson_discussion_comments_parent
        FOREIGN KEY (parent_id) REFERENCES lesson_discussion_comments(id)
);

CREATE INDEX IF NOT EXISTS idx_lesson_discussion_comments_course_lesson
    ON lesson_discussion_comments(course_lesson_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_lesson_discussion_comments_parent
    ON lesson_discussion_comments(parent_id, created_at ASC);

CREATE TABLE IF NOT EXISTS lesson_discussion_comment_likes (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP,
    updated_by UUID,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    comment_id UUID NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT fk_lesson_discussion_comment_likes_comment
        FOREIGN KEY (comment_id) REFERENCES lesson_discussion_comments(id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_discussion_comment_likes_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_lesson_discussion_comment_likes_comment_user
        UNIQUE (comment_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_lesson_discussion_comment_likes_comment
    ON lesson_discussion_comment_likes(comment_id);
