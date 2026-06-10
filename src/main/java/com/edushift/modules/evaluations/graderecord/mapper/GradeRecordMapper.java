package com.edushift.modules.evaluations.graderecord.mapper;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordListItem;
import com.edushift.modules.evaluations.graderecord.dto.GradeRecordResponse;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.students.entity.Student;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper between {@link GradeRecord} and the DTOs exposed by
 * the API. Following project convention (see
 * {@code .cursor/skills/create-backend-module/SKILL.md}) we don't use
 * MapStruct — keeping the mapping explicit lets readers follow the wire
 * shape without an extra layer of generated code.
 */
@Component
public class GradeRecordMapper {

    public GradeRecordResponse toResponse(GradeRecord grade) {
        Evaluation evaluation = grade.getEvaluation();
        Student student = grade.getStudent();

        return new GradeRecordResponse(
                grade.getPublicUuid(),
                new GradeRecordResponse.EvaluationRef(
                        evaluation.getPublicUuid(),
                        evaluation.getName(),
                        evaluation.getScale(),
                        evaluation.getStatus()
                ),
                new GradeRecordResponse.StudentRef(
                        student.getPublicUuid(),
                        student.getFirstName(),
                        student.getLastName(),
                        student.getSecondLastName()
                ),
                grade.getScore(),
                grade.getLiteral(),
                grade.getComments(),
                grade.getRecordedAt(),
                grade.getRecordedByUserId(),
                grade.getIsActive(),
                grade.getCreatedAt(),
                grade.getUpdatedAt()
        );
    }

    public GradeRecordListItem toListItem(GradeRecord grade) {
        Student student = grade.getStudent();
        return new GradeRecordListItem(
                grade.getPublicUuid(),
                student.getPublicUuid(),
                student.getFirstName(),
                student.getLastName(),
                student.getSecondLastName(),
                grade.getScore(),
                grade.getLiteral(),
                grade.getComments(),
                grade.getRecordedAt(),
                grade.getIsActive(),
                grade.getCreatedAt(),
                grade.getUpdatedAt()
        );
    }
}
