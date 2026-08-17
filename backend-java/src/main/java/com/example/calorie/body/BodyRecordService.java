package com.example.calorie.body;

import com.example.calorie.body.dto.BodyDtos.BodyRecordRequest;
import com.example.calorie.body.dto.BodyDtos.BodyRecordResponse;
import com.example.calorie.common.DateRanges;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BodyRecordService {

    private final BodyRecordRepository repository;

    public BodyRecordService(BodyRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<BodyRecordResponse> list(Long userId, LocalDate from, LocalDate to) {
        DateRanges.requireValid(from, to);
        return repository.findByUserIdAndRecordedOnBetweenOrderByRecordedOnAsc(userId, from, to)
                .stream()
                .map(BodyRecordResponse::from)
                .toList();
    }

    /** 1 日 1 レコード。同じ日に再度記録した場合は上書きする。 */
    @Transactional
    public BodyRecordResponse put(Long userId, LocalDate date, BodyRecordRequest request) {
        BodyRecord record = repository.findByUserIdAndRecordedOn(userId, date)
                .map(existing -> {
                    existing.update(request.weightKg(), request.bodyFatPct());
                    return existing;
                })
                .orElseGet(() -> repository.save(BodyRecord.create(
                        userId, date, request.weightKg(), request.bodyFatPct())));

        return BodyRecordResponse.from(record);
    }

    /** 削除。元から無い場合もエラーにしない（冪等）。 */
    @Transactional
    public void delete(Long userId, LocalDate date) {
        repository.deleteByUserIdAndRecordedOn(userId, date);
    }
}
