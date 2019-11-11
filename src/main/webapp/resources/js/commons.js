function fechaPop(vpopVarStr) {

	$(function() {
		if (document.getElementById('systemErrorMessages') == null) {
			PF(vpopVarStr).hide();
		}
	});
}

function abrePop(vpopVarStr) {

	$(function() {
		if (document.getElementById('systemErrorMessages') == null) {
			PF(vpopVarStr).show();
		}
	});

}
