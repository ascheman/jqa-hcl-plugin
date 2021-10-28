# TODO handle implicit objects/attributes like "id"
data "aws_vpc" "this" {}

data "aws_subnet_ids" "this" {
  vpc_id = data.aws_vpc.this.id
}

locals {
  default_subnet_id = "dummy"
  # tolist(data.aws_subnet_ids.this.ids)[0]
}

resource "aws_network_interface" "this" {
  subnet_id = local.default_subnet_id
}

resource "aws_eip" "this" {
  network_interface = aws_network_interface.this.id
}

resource "aws_lb" "this" {
  name = "LB-dummy"
  load_balancer_type = "network"

  subnet_mapping {
    subnet_id     = local.default_subnet_id
    allocation_id = aws_eip.this.id
  }
}
