package com.example.jpa.repository;

import com.example.jpa.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByName(String name);

    @Query("SELECT t FROM Team t LEFT JOIN FETCH t.members WHERE t.id = :id")
    Optional<Team> findByIdWithMembers(@Param("id") Long id);

    @Query("SELECT DISTINCT t FROM Team t LEFT JOIN FETCH t.members")
    List<Team> findAllWithMembers();
}
