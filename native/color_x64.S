# Windows x64 leaf functions. Keep the ENDBR64 landing pad when patching.
# The dedicated 16-byte NOP area is known in advance: no arbitrary instruction
# decoding or relocation is needed for this demonstration.
.text
.p2align 4, 0x90
.globl snake_color_target
.def snake_color_target; .scl 2; .type 32; .endef
snake_color_target:
    .byte 0xf3, 0x0f, 0x1e, 0xfa
    .rept 16
    nop
    .endr
    movl $0x63edb4, %eax
    ret

.p2align 4, 0x90
.globl snake_color_replacement
.def snake_color_replacement; .scl 2; .type 32; .endef
snake_color_replacement:
    .byte 0xf3, 0x0f, 0x1e, 0xfa
    movl $0xbb86fc, %eax
    ret
