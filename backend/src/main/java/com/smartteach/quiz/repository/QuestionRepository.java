package com.smartteach.quiz.repository;

import com.smartteach.quiz.entity.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * Questions with their options in one query. Accepted answers need a second query: Hibernate
     * cannot fetch-join two lists at once (MultipleBagFetchException), and joining both would
     * multiply the rows anyway.
     */
    @Query("select q from Question q left join fetch q.options where q.quizId = :quizId order by q.position")
    List<Question> findWithOptions(Long quizId);

    /** Fills {@code acceptedAnswers} of the questions already loaded by {@link #findWithOptions}. */
    @Query("select q from Question q left join fetch q.acceptedAnswers where q.quizId = :quizId")
    List<Question> fetchAcceptedAnswers(Long quizId);

    /** Options and accepted answers are removed by ON DELETE CASCADE. */
    @Modifying
    @Query("delete from Question q where q.quizId = :quizId")
    void deleteByQuizId(Long quizId);
}
