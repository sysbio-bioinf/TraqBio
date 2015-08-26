(function(){
    $(document).ready(function() {
        $('.hover-popover').popover({
            trigger: 'hover'
        });

        $('.newTemplateToggle').on('click', function(){
            $('#newTemplateWrapper').toggle('fast');
        });

        if (!Array.prototype.last){
            Array.prototype.last = function(){
                return this[this.length - 1];
            };
        }

        var templateRow = $('#template_row').html();
        var successTemplate = '<div class="alert alert-success"><% message %></div>';
        var errorTemplate = '<div class="alert alert-danger"><% message %></div>';

        function creatingFailed(msg) {
            var alert;
            if (msg.hasOwnProperty("responseJSON"))  {
                alert = {message: "Failure: " + msg.responseJSON.error };
            }
            else if (msg.hasOwnProperty("responseText")) {
                alert = {message: "Failure: " + msg.responseText };
            }
            else {
                alert = {message: "Unknown reason."};
            }

            var content = $('#after-creation-content');
            content.html(Mustache.render(errorTemplate, alert));
            $('.after-creation')
                .modal()
                .on('hidden.bs.modal', function (e) {
                    window.location = serverRoot + '/';
                });
        }

        function templateCreated(msg){
            var alert = { message: "Success" };
            var content = $('#after-creation-content');
            content.before(Mustache.render(successTemplate, alert));
 
            $('.after-creation')
                .modal()
                .on('hidden.bs.modal', function (e) {
                    window.location = serverRoot + '/';
                });
        }

       
        var renderTemplateSteps = function(tBody, template) {
            $.each(template.templatesteps, function(index, step){
                var row = Mustache.render(templateRow, step);
                tBody.append(row);
            });
        };

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

        function normalizeSteps(template) {

            for (var key in template.templatesteps) {
                delete template.templatesteps[key]['id'];
                template.templatesteps[key].state = 0;
            }

            return template;
        }     

        
        var templateLoaded = function(template) {
            var idCounter = 0;
            var tBody = $('#step-table tbody');
            var templateData = $('#templateData');
            
            templateData.find('.customize').each(
            		function(){
            			var name = $(this).data().name;
            			if( name in template  && ! (name == "name"))
            				$(this).val( template[name] );
            			else{
            				template[name] = $(this).val();                            
            			}
            			});
            
            tBody.empty();

            renderTemplateSteps(tBody, template);

            function bindHandler() {
                tBody.find('.btn-delete-row').on('click', deleteRow);
                tBody.find('.btn-row-up').on('click', upRow);
                tBody.find('.btn-row-down').on('click', downRow);
                tBody.find('input, textarea').on('change', stepDataBinding);
            }

            function bodyDataBinding(e) {
                var name  = $(this).data().name;
                var value = $(this).val();
           
                template[name] = value;
            }

            function stepDataBinding(e){
                var value = $(this).val(),
                    name  = $(this).data().name,
                    id    = $(this).data().id,
                    steps  = template.templatesteps;

                for (var i = 0; i <  steps.length; i++) {
                    if (steps[i].id == id) {
                        steps[i][name] = value;
                    }
                }
            }

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
            

            function createTemplate(e) {
                var formErrors = $('#templateData').validator('validate').find('.has-error');
                if (formErrors.length > 0) {
                    $('html, body').animate({
                        scrollTop: formErrors[0].offsetTop
                    }, 300);
                }
                else{
                    $.ajax({
                        url: serverRoot + '/template/create',
                        type: 'POST',
                        contentType: "application/json; charset=utf-8",
                        data: JSON.stringify(normalizeSteps(template)),
                        success: templateCreated,
                        error: creatingFailed
                    });
                }
            }
            
            function success(e) {
                window.location = serverRoot + '/template/list'
            }

            $('.btn-add-row').off('click').on('click', addRow);
            templateData.find('.customize').on('change', bodyDataBinding);
            bindHandler();
            $('#createTemplate').off('click').on('click', createTemplate);
        };

        $('#selectTemplate').on('change', function(e) {
            var id = parseInt(e.currentTarget.value , 10);
            if (id > 0) {
                $.ajax({
                    url: serverRoot + '/template/' + id,
                    type: 'GET',
                    contentType: "application/json; charset=utf-8",
                    success: templateLoaded
                });
            } else {
                templateLoaded({
                    description:"",
                    templatesteps:[]
                });
            }
        });

        templateLoaded({
            description:"",
            templatesteps:[]
        });

    });
}());
