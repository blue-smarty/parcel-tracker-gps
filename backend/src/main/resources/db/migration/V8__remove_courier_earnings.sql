UPDATE parcels
SET proof_of_delivery = proof_of_delivery - 'tipAmount' - 'payoutAmount'
WHERE proof_of_delivery ? 'tipAmount' OR proof_of_delivery ? 'payoutAmount';
