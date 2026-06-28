function moveFocus(formName) {

    if (!formName) return;

    const selector = "#" + formName;

    // ===== KEYDOWN：控制移動 =====
    let keydownSelectName = selector + " input, " + selector + " textarea, " + selector + " select";
    $(document).on("keydown", keydownSelectName, function (e) {

        const $inputs = $(selector)
            .find("input, textarea, select")
            .filter(":visible:not(:disabled)");

        const idx = $inputs.index(this);
        let nextidx = -1;

        switch (e.key) {

            case "Enter":
            case "ArrowRight":
            case "ArrowDown":
                e.preventDefault();
                nextidx = idx + 1;
                break;

            case "Backspace":
                if (this.value.length === 0) {
                    e.preventDefault();
                    nextidx = idx - 1;
                }
                break;
            case "ArrowLeft":
            case "ArrowUp":
                e.preventDefault();
                nextidx = idx - 1;
                break;
        }

        if (nextidx >= 0) {
            const next = $inputs.eq(nextidx);
            if (next.length) next.focus();
        }
    });

    // ===== INPUT：maxlength 自動跳 =====
    let inputSelectName = selector + " input, " + selector + " textarea";
    $(document).on("input", inputSelectName, function () {

        const max = this.maxLength;

        if (max > 0 && this.value.length >= max) {

            const $inputs = $(selector)
                .find("input, textarea, select")
                .filter(":visible:not(:disabled)");

            const idx = $inputs.index(this);
            const next = $inputs.eq(idx + 1);

            if (next.length) next.focus();
        }
    });
}