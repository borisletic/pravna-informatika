package rs.ftn.pi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.ftn.pi.model.CaseEntity;

@Repository
public interface CaseRepository extends JpaRepository<CaseEntity, Long> {
}
