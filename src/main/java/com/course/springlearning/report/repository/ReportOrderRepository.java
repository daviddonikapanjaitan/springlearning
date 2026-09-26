package com.course.springlearning.report.repository;

import com.course.springlearning.report.entity.ReportOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportOrderRepository extends JpaRepository<ReportOrder, Long> {

    Page<ReportOrder> findAllByUser_Id(Long userId, Pageable pageable);
}
