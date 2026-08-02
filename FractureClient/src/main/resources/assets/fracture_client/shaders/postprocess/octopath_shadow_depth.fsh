#version 460 core

// Depth is written by the fixed-function depth stage; this pass intentionally has no colour
// output. Point-light atlas tiles attach depth only, so there is no unused colour target.
void main() {
}
