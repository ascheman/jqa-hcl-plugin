// Find all Terraform Resources and count
MATCH (b:HCL:Terraform:Resource) -[:HAS_NAMES]->(n)
  WHERE n.order = 1
RETURN DISTINCT n.value, count(b) ORDER BY n.value
