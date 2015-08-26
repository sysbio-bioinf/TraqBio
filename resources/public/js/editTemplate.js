(function() {
    var oldtemplate = {
                templatesteps: []
        };
        template = {
    		templatesteps: []
        };
    $(document).ready(function () {
    	
    	if (!Array.prototype.last){
            Array.prototype.last = function(){
                return this[this.length - 1];
            };
        }
    	
    	var tBody = $('#step-table tbody');
    	var idCounter = 0;
    	
    	function initializeTemplate(){
            $('.template-init').each(function(){
                var name  = $(this).data().name;
                var value = $(this).text();
                
                template[name] = value;
                oldtemplate[name] = value;
            });
            $('.template-steps-init').each(templateStepsInit);
        }
        
        function templateStepsInit(){
            var id    = $(this).data().id;
            template.templatesteps.push({id: id});
            oldtemplate.templatesteps.push({id: id});
            $(this).find("div").each(function(){
                var name = $(this).data().name,
                    val;

                if (name === "sequence"){
                    val = parseInt($(this).text())
                } else {
                	val = $(this).text();
                }
             
                for (var key in template.templatesteps) {
                    if (template.templatesteps[key].id == id) {
                    	template.templatesteps[key][name] = val;
                    	oldtemplate.templatesteps[key][name] = val;
                    }
                }
            });
        }

        function templateDataBinding(e) {
            var name  = $(this).data().name;
            var value = $(this).val();

            template[name] = value;
        }

        function templateStepDataBinding(e) {
            var name  = $(this).data().name;
            var id    = $(this).data().id;
            var val =  $(this).val();

            for (var key in template.templatesteps) {
                if (template.templatesteps[key].id == id) {
                	template.templatesteps[key][name] = val;
                }
            }
        }
        
        
        var renderTemplateSteps = function(tBody, template) {
        	var templateRow = $('#template_row').html();
        	
            $.each(template.templatesteps, function(index, step){
                var row = Mustache.render(templateRow, step);
                tBody.append(row);
            });
        };
        
        
        function redraw() {
            tBody.empty();
            renderTemplateSteps(tBody, template);
            bindHandler();
        }

        function deleteRow (e){
            var id = $(this).data().id;
            deleteTemplateStep(id, template);
            redraw();
        }

        function addRow(e) {
            var last = template.templatesteps.last(),
                newSeq;
            if (last) {
                newSeq = last.sequence + 1;
            } else {
                newSeq = 1;
            }

            template.templatesteps.push({
                id: --idCounter,
                type: "",
                description:"",
                sequence: newSeq
            });
            redraw();
        }
        
        function compareSteps(f, s) {
            return (f.sequence < s.sequence) ? -1 : 1;
        }
        
        function deleteTemplateStep (id, template) {
            var newSteps = [],
                steps = template.templatesteps;

            steps.sort(compareSteps);
            var seq = 1;
            for (var i = 0; i <  steps.length; i++) {
                if (steps[i].id != id) {
                    steps[i].sequence = seq++;
                    newSteps.push(steps[i]);
                }
            }
            template.templatesteps = newSteps;
        }
        
        function upRow(e) {
            var seq    = $(this).data().sequence,
                before = seq - 1,
                steps  = template.templatesteps;
            if (seq != 1) {
                for (var i = 0; i <  steps.length; i++) {
                    if (steps[i].sequence == before) {
                        steps[i].sequence = steps[i].sequence + 1;
                    } else if (steps[i].sequence == seq) {
                        steps[i].sequence = steps[i].sequence - 1;
                    }
                }
                steps.sort(compareSteps);
                template.templatesteps = steps;
                redraw();
            }
        }
        
        function downRow(e) {
            var seq   = $(this).data().sequence,
                after = seq + 1,
                steps = template.templatesteps;
            if (seq != steps.length) {
                for (var i = 0; i <  steps.length; i++) {
                    if (steps[i].sequence == seq) {
                        steps[i].sequence = steps[i].sequence + 1;
                    } else if (steps[i].sequence == after) {
                        steps[i].sequence = steps[i].sequence - 1;
                    }
                }
                steps.sort(compareSteps);
                template.templatesteps = steps;
                redraw();
            }
        }

        function putTemplate(e) {
        	 var formErrors = $('#templateData').validator('validate').find('.has-error');
             if (formErrors.length > 0) {
                 $('html, body').animate({
                     scrollTop: formErrors[0].offsetTop
                 }, 300);
             }
             else{	
    	        $.ajax({
    	            url: serverRoot + '/template/' + template.id,
    	            type: 'PUT',
    	            contentType: "application/json; charset=utf-8",
    	            data: JSON.stringify({template: template, oldtemplate: oldtemplate}),
    	            success: success
    	        });
             }
        }

        function success(e) {
            window.location = serverRoot + '/template/list'
        }
    	
    	
    	function bindHandler() {
            tBody.find('.btn-delete-row').on('click', deleteRow);
            tBody.find('.btn-row-up').on('click', upRow);
            tBody.find('.btn-row-down').on('click', downRow);
            tBody.find('input, textarea').on('change', templateStepDataBinding);
        }
    	
    	initializeTemplate();
    	renderTemplateSteps( tBody, template );
    	bindHandler();

        $('.customize').on('change', templateDataBinding);

        $('.customizeStep').on('change', templateStepDataBinding);

        $('#updateTemplate').on('click', putTemplate);
        
        $('.btn-add-row').off('click').on('click', addRow);
    });
}());