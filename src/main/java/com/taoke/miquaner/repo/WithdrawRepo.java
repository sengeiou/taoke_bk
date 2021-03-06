package com.taoke.miquaner.repo;

import com.taoke.miquaner.data.EUser;
import com.taoke.miquaner.data.EWithdraw;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WithdrawRepo extends JpaRepository<EWithdraw, Long> {

    List<EWithdraw> findAllByUserEquals(EUser user);

    Page<EWithdraw> findAllByPayedEquals(Boolean payed, Pageable pageable);

    List<EWithdraw> findAllByPayedEquals(Boolean payed);

}
