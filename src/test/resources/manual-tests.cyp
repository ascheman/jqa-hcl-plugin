// Find all Terraform Resources and count
MATCH (b:HCL:Terraform:Resource) -[:HAS_NAMES]->(n)
  WHERE n.order = 1
RETURN DISTINCT n.value, count(b) ORDER BY n.value


MATCH (r:HCL {identifier:'resource'}) RETURN r

MATCH (r:HCL {name:'aws_eip'}) -[:HAS_BLOCK]-> (eip:HCL) RETURN *

MATCH (r:HCL {name:'aws_lb'})
        -[:HAS_BLOCK]-> (aws_lb:HCL)
        -[:HAS_BLOCK]-> (subnet_mapping {name: 'subnet_mapping'})
        -[:HAS_ATTRIBUTE]-> (allocation_id {identifier: 'allocation_id'})
RETURN *

MATCH (aws_lbs:HCL {name:'aws_lb'})
//        -[:HAS_BLOCK]-> (aws_lb:HCL)
//        -[:HAS_BLOCK]-> (subnet_mapping {name: "subnet_mapping"})
//        -[:HAS_ATTRIBUTE]-> (allocation_id {identifier: "allocation_id"})
        -[:REFERS_TO]-> (id {identifier: 'id'})
        <-[:HAS_ATTRIBUTE]- (eip:HCL)
        <-[:HAS_BLOCK]- (aws_eips:HCL {name: 'aws_eip'})
    RETURN eip
UNION

MATCH  (eip) -[:HAS_ATTRIBUTE]-> (network_interface:HCL {identifier: 'network_interface'})
    RETURN eip

// Funktioniert so nicht
MATCH (aws_lbs:HCL {name:'aws_lb'})
        -[:REFERS_TO]-> (id {identifier: 'id'})
        <-[:HAS_ATTRIBUTE]- (eip:HCL)
        <-[:HAS_BLOCK]- (aws_eips:HCL {name: 'aws_eip'})
WITH eip
MATCH  (eip) -[:HAS_ATTRIBUTE]-> (network_interface:HCL {identifier: 'network_interface'})
WITH eip
MATCH (f:HCL:File) -[] -> (eip)
RETURN f, eip

MATCH (aws_lbs:HCL {name:'aws_lb'})
        -[:REFERS_TO]-> (id {identifier: 'id'})
        <-[:HAS_ATTRIBUTE]- (eip:HCL)
        <-[:HAS_BLOCK]- (aws_eips:HCL {name: 'aws_eip'})
RETURN eip
UNION
MATCH  (eip) -[:HAS_ATTRIBUTE]-> (network_interface:HCL {identifier: 'network_interface'})
//RETURN eip
WITH eip
MATCH (f:HCL:File) -[] -> (eip)
RETURN f, eip


MATCH (aws_lbs:HCL {name:'aws_lb'})
        -[:REFERS_TO]-> (id {identifier: 'id'})
        <-[:HAS_ATTRIBUTE]- (eip:HCL)
        <-[:HAS_BLOCK]- (aws_eips:HCL {name: 'aws_eip'})
WHERE EXISTS {
  MATCH  (eip)-[:HAS_ATTRIBUTE]->(network_interface:HCL {identifier: 'network_interface'})
}
RETURN eip
//WITH eip
//MATCH (f:HCL:File) -[] -> (eip)
//RETURN f, eip

MATCH (aws_lbs:HCL {name:'aws_lb'})
//        -[:HAS_BLOCK]->(aws_lb:HCL)
//        -[:HAS_BLOCK]->(subnet_mapping {name: 'subnet_mapping'})
        -[*]->()
        -[:REFERS_TO]-> (id {identifier: 'id'})
        <-[:HAS_ATTRIBUTE]- (eip:HCL)
        <-[:HAS_BLOCK]- (aws_eips:HCL {name: 'aws_eip'})
WITH eip
MATCH (f:HCL:File) -[*]-> (eip) -[:HAS_ATTRIBUTE]-> (network_interface:HCL {identifier: 'network_interface'})
WHERE NOT (f:Configuration)
RETURN f, eip
//RETURN f.fileName, eip.name

WITH eip
MATCH (f:HCL:File) -[]-> (eip)
RETURN f, eip
