        .syntax unified
        .text
        .code 32

        .func _interrupt_vectors
_interrupt_vectors:
        CDP p7, 1, cr0, cr0, cr0, #0 // Reset
        CDP p7, 2, cr0, cr0, cr0, #0 // Undefined
        CDP p7, 3, cr0, cr0, cr0, #0 // Supervisor Call
        CDP p7, 4, cr0, cr0, cr0, #0 // Prefetch Abort
        CDP p7, 5, cr0, cr0, cr0, #0 // Data Abort
        CDP p7, 6, cr0, cr0, cr0, #0 // Hypervisor Trap (unsupported)
        CDP p7, 7, cr0, cr0, cr0, #0 // IRQ
        CDP p7, 8, cr0, cr0, cr0, #0 // FIQ
        .endfunc
