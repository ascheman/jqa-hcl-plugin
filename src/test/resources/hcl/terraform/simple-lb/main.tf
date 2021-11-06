resource "aws_network_interface" "this" {
  subnet_id = var.default_subnet_id
}

resource "aws_eip" "this" {
  network_interface = aws_network_interface.this.id
}

resource "aws_lb" "this" {
  name               = "LB-dummy"
  load_balancer_type = "network"

  subnet_mapping {
    subnet_id     = var.default_subnet_id
    allocation_id = aws_eip.this.id
  }
}
