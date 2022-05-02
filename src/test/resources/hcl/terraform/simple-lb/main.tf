resource "aws_lb" "this" {
  name               = "LB-dummy"
  load_balancer_type = "network"

  subnet_mapping {
    subnet_id     = local.default_subnet_id
    allocation_id = aws_eip.this.id
  }
}
