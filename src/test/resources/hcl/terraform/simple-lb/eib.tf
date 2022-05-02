resource "aws_network_interface" "this" {
  subnet_id = local.default_subnet_id
}

resource "aws_eip" "this" {
  network_interface = aws_network_interface.this.id
}
